package lyl.reactnativeaudio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import com.facebook.react.bridge.*

class AudioModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  companion object {
    // Events
    const val PLAYER_STATE_EVENT = "player-state"
    const val PLAYER_PROGRESS_EVENT = "player-progress"
    const val PLAYER_DURATION_EVENT = "player-duration"

    // Player States
    const val PLAYER_STATE_PLAYING = "playing"
    const val PLAYER_STATE_PAUSED = "paused"
    const val PLAYER_STATE_BUFFERING = "buffering"
    const val PLAYER_STATE_ENDED = "ended"
    const val PLAYER_STATE_UNKNOWN = "unknown"
  }

  private val context = reactContext
  private var service: AudioService? = null

  override fun getName(): String { return "Audio" }

  override fun hasConstants(): Boolean { return true }

  override fun getConstants(): MutableMap<String, Any> {
    return mutableMapOf(
      "PLAYER_STATE_EVENT" to PLAYER_STATE_EVENT,
      "PLAYER_PROGRESS_EVENT" to PLAYER_PROGRESS_EVENT,
      "PLAYER_DURATION_EVENT" to PLAYER_DURATION_EVENT,

      "PLAYER_STATE_PLAYING" to PLAYER_STATE_PLAYING,
      "PLAYER_STATE_PAUSED" to PLAYER_STATE_PAUSED,
      "PLAYER_STATE_BUFFERING" to PLAYER_STATE_BUFFERING,
      "PLAYER_STATE_ENDED" to PLAYER_STATE_ENDED,
      "PLAYER_STATE_UNKNOWN" to PLAYER_STATE_UNKNOWN
    )
  }

  // Module CMD

  @ReactMethod
  fun play(data: ReadableMap, promise: Promise) {
    try {
      val string = data.getString("uri") ?: throw IllegalArgumentException("uri field required")
      val uri = Uri.parse(string)

      if (uri == service?.audio?.uri) return resume(promise)

      val audio = AudioService.Audio(uri, data.toHashMap())
      bindAudioService(audio)

      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject(e)
    }
  }

  @ReactMethod
  fun update(data: ReadableMap, promise: Promise) {
    context.runOnUiQueueThread {
      service?.prepareNotification(context, data.toHashMap())
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun seekTo(position: Double, promise: Promise) {
    context.runOnUiQueueThread {
      service?.seekTo(position)
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun pause(promise: Promise) {
    context.runOnUiQueueThread {
      service?.playWhenReady(false)
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun resume(promise: Promise) {
    context.runOnUiQueueThread {
      service?.playWhenReady(true)
      promise.resolve(null)
    }
  }

  @ReactMethod
  fun stop(promise: Promise) {
    context.runOnUiQueueThread {
      service?.stop()
      unbindAudioService()
      promise.resolve(null)
    }
  }

  // Audio Service Binding

  private val connection = object : ServiceConnection {

    var audio: AudioService.Audio? = null

    override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
      val binder = binder as AudioService.AudioServiceBinder
      service = binder.service

      val audio = audio ?: return
      prepareAudioService(audio)
    }

    override fun onServiceDisconnected(name: ComponentName?) {
      service = null
    }
  }

  private fun bindAudioService(audio: AudioService.Audio) {
    if (service != null) return prepareAudioService(audio)

    connection.audio = audio
    Intent(context, AudioService::class.java).also { intent ->
      context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
  }

  private fun unbindAudioService() {
    if (service == null) return
    context.unbindService(connection)
    service = null
  }

  private fun prepareAudioService(audio: AudioService.Audio) {
    context.runOnUiQueueThread {
      service?.preparePlayer(context, audio)
    }
  }
}
