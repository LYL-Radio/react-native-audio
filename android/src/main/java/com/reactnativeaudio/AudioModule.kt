package com.reactnativeaudio

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import com.facebook.react.bridge.*

class AudioModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

  companion object {

    // Metadata Keys
    const val METADATA_URI_KEY = "uri"
  }

  private val context = reactContext
  private var service: AudioService? = null

  override fun getName(): String { return "Audio" }

  // Module CMD

  @ReactMethod
  fun play(data: ReadableMap, promise: Promise) {
    try {
      val string = data.getString(METADATA_URI_KEY) ?: throw IllegalArgumentException("uri field required")
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

  // Audio Service

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
