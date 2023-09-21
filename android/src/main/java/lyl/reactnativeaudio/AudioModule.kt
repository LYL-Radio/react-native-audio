package lyl.reactnativeaudio

import android.content.ComponentName
import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlin.math.max

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
  private lateinit var controllerFuture: ListenableFuture<MediaController>
  private val controller: MediaController?
    get() = if (controllerFuture.isDone) controllerFuture.get() else null
  private var progressHandler: Handler? = null

  override fun getName() = "Audio"
  override fun hasConstants() = true

  override fun initialize() {
    super.initialize()

    controllerFuture =
      MediaController.Builder(
        context,
        SessionToken(context, ComponentName(context, AudioService::class.java))
      ).buildAsync()

    controllerFuture.addListener({
      controller?.addListener(playerListener)
    }, MoreExecutors.directExecutor())
  }

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

  private fun sendEvent(eventName: String, params: Any?) {
    context
      .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      ?.emit(eventName, params)
  }

  private fun startProgressListener() {
    if (progressHandler != null) return
    val controller = controller ?: return
    progressHandler = Handler(controller.applicationLooper)
    progressHandler?.post(onPlayerProgressChanged)
  }

  private fun stopProgressListener() {
    progressHandler?.removeCallbacks(onPlayerProgressChanged)
    progressHandler = null
  }

  private fun metadata(data: ReadableMap): MediaMetadata {
    return MediaMetadata.Builder()
      .setTitle(data.getString("title"))
      .setAlbumTitle(data.getString("album"))
      .setArtist(data.getString("artist"))
      .setAlbumArtist(data.getString("albumArtist"))
      .setArtworkUri(
        data.getString("artwork").let { Uri.parse(it) }
      )
      .build()
  }

  // Module CMD

  @ReactMethod
  fun source(data: ReadableMap, promise: Promise) {
    try {
      val controller = controller ?: throw IllegalArgumentException("Audio module not initialized")
      val uri = data.getString("uri") ?: throw IllegalArgumentException("uri field required")

      // Build the media item.
      val mediaItem = MediaItem.Builder()
        .setUri(uri)
        .setMediaId(uri)
        .setMediaMetadata(metadata(data))
        .build()

      if (uri == controller.currentMediaItem?.mediaId) {
        controller.replaceMediaItem(0, mediaItem)
        return promise.resolve(null)
      }

      // Set the media item to be played.
      controller.setMediaItem(mediaItem)
      promise.resolve(null)
    } catch (e: Exception) {
      promise.reject(e)
    }
  }

  @ReactMethod
  fun play(promise: Promise) {
    controller?.playWhenReady = true
    controller?.prepare()
    promise.resolve(null)
  }

  @ReactMethod
  fun seekTo(position: Double, promise: Promise) {
    val positionMs = position.toLong() * 1000
    controller?.seekTo(positionMs)
    promise.resolve(null)
  }

  @ReactMethod
  fun pause(promise: Promise) {
    controller?.playWhenReady = false
    promise.resolve(null)
  }

  @ReactMethod
  fun stop(promise: Promise) {
    controller?.playWhenReady = false
    controller?.stop()
    controller?.seekTo(0)
    promise.resolve(null)
  }

  // Player.Listener

  private val playerListener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) {
      if (isPlaying) startProgressListener()
      else stopProgressListener()
    }

    override fun onEvents(player: Player, events: Player.Events) {
      if (events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)) {
        when (player.playbackState) {
          Player.STATE_BUFFERING -> sendEvent(PLAYER_STATE_EVENT, PLAYER_STATE_BUFFERING)
          Player.STATE_ENDED -> sendEvent(PLAYER_STATE_EVENT, PLAYER_STATE_ENDED)
          Player.STATE_IDLE -> {}
          Player.STATE_READY -> {}
        }
      }

      if (events.contains(Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) {
        sendEvent(PLAYER_STATE_EVENT, PLAYER_STATE_PLAYING)
      }

      if (events.contains(Player.EVENT_PLAY_WHEN_READY_CHANGED) && !player.playWhenReady) {
        sendEvent(PLAYER_STATE_EVENT, PLAYER_STATE_PAUSED)
      }

      if (events.contains(Player.EVENT_TIMELINE_CHANGED) && player.duration != C.TIME_UNSET) {
        val duration = player.duration / 1000
        sendEvent(PLAYER_DURATION_EVENT, duration.toDouble())
      }

      if (events.contains(Player.EVENT_PLAYER_ERROR)) {
        sendEvent(PLAYER_STATE_EVENT, PLAYER_STATE_UNKNOWN)
      }
    }
  }

  // Progress Listener

  private val onPlayerProgressChanged = object : Runnable {
    override fun run() {
      val controller = controller ?: return
      progressHandler?.postDelayed(this, 200)

      val position = max(0, controller.currentPosition / 1000)
      sendEvent(PLAYER_PROGRESS_EVENT, position.toDouble())
    }
  }
}
