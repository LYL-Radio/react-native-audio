package lyl.reactnativeaudio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.*
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.MainThread
import com.facebook.react.HeadlessJsTaskService
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.ReactContext
import com.facebook.react.jstasks.HeadlessJsTaskConfig
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ext.mediasession.TimelineQueueNavigator
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Util
import java.net.URL
import kotlin.math.max

class AudioService: HeadlessJsTaskService(), Player.Listener {

  data class Audio(val uri: Uri,
                   var metadata: HashMap<String, Any>,
                   var playWhenReady: Boolean = true,
                   var bitmap: Bitmap? = null)

  inner class AudioServiceBinder: Binder() {
    val service get() = this@AudioService
  }

  companion object {
    const val PLAYBACK_CHANNEL_ID = "react-native-audio-playback-channel"
    const val PLAYBACK_NOTIFICATION_ID = 1
    const val MEDIA_SESSION_TAG = "react-native-audio-media-session"
  }

  var audio: Audio? = null
    private set

  private lateinit var player: ExoPlayer
  private var progressHandler: Handler? = null

  private val notification = object {
    var manager: PlayerNotificationManager? = null
    var mediaSessionCompat: MediaSessionCompat? = null
    var mediaSessionConnector: MediaSessionConnector? = null
    var bitmapTask: AsyncTask<Void, Void, Bitmap?>? = null
  }

  private val reactContext: ReactContext?
    get() = reactNativeHost.reactInstanceManager.currentReactContext

  private fun sendEvent(eventName: String, params: Any?) {
    reactContext
      ?.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
      ?.emit(eventName, params)
  }

  /**
   * Called from [.onStartCommand] to create a [HeadlessJsTaskConfig] for this intent.
   *
   * @param intent the [Intent] received in [.onStartCommand].
   * @return a [HeadlessJsTaskConfig] to be used with [.startTask], or `null` to
   * ignore this command.
   */
  override fun getTaskConfig(intent: Intent?): HeadlessJsTaskConfig? {
    return HeadlessJsTaskConfig("react-native-audio", Arguments.createMap(), 0, true)
  }

  /**
   * Called by the system when the service is first created.  Do not call this method directly.
   */
  override fun onCreate() {
    super.onCreate()

    player = SimpleExoPlayer.Builder(this).build()
    player.addListener(this)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

      val name = reactContext?.applicationInfo?.let {
        applicationContext.packageManager.getApplicationLabel(it)
      }

      val channel = NotificationChannel(PLAYBACK_CHANNEL_ID,
        name ?: "Audio Service",
        NotificationManager.IMPORTANCE_DEFAULT
      )

      channel.setShowBadge(false)
      channel.setSound(null, null)
      val manager = reactContext?.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      manager?.createNotificationChannel(channel)
    }
  }

  override fun onDestroy() {
    notification.mediaSessionCompat?.release()
    notification.mediaSessionConnector?.setPlayer(null)
    notification.manager?.setPlayer(null)
    player.release()
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder {
    return AudioServiceBinder()
  }

  // Service CMD

  @MainThread
  fun preparePlayer(context: Context, audio: Audio) {
    this.audio = audio

    val factory = DefaultHttpDataSource.Factory().setUserAgent(Util.getUserAgent(context, "react-native-audio"))
    val item = MediaItem.fromUri(audio.uri)
    val source = DefaultMediaSourceFactory(factory).createMediaSource(item)

    player.setMediaSource(source)
    player.prepare()

    playWhenReady(audio.playWhenReady)
    prepareNotification(context, audio.metadata)
  }

  @MainThread
  fun prepareNotification(context: Context, metadata: HashMap<String, Any>) {
    audio?.metadata = metadata

    val title= metadata["title"] as? String ?: " No title"
    val artwork= metadata["artwork"] as? String
    val album= metadata["album"] as? String
    val artist= metadata["artist"] as? String
    val albumArtist= metadata["albumArtist"] as? String
    val text= metadata["text"] as? String
    val subtext= metadata["subtext"] as? String

    val player = object : ForwardingPlayer(player) {

      override fun setPlayWhenReady(playWhenReady: Boolean) {
        this@AudioService.playWhenReady(playWhenReady)
      }

      override fun stop() {
        this@AudioService.stop()
      }
    }

    val adapter = object : PlayerNotificationManager.MediaDescriptionAdapter {

      override fun createCurrentContentIntent(player: Player): PendingIntent? {
        val intent = Intent(context, AudioService::class.java)
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
      }

      override fun getCurrentContentTitle(player: Player): String {
        return title
      }

      override fun getCurrentContentText(player: Player): String? {
        return text ?: artist
      }

      override fun getCurrentSubText(player: Player): CharSequence? {
        return subtext ?: album
      }

      override fun getCurrentLargeIcon(player: Player, callback: PlayerNotificationManager.BitmapCallback): Bitmap? {
        audio?.bitmap?.let { return it }
        val url = artwork ?: return null

        notification.bitmapTask = BitmapDownloaderTask(url) { bitmap ->
          callback.onBitmap(bitmap)
          audio?.bitmap = bitmap
        }.execute()

        return null
      }
    }

    val listener = object : PlayerNotificationManager.NotificationListener {

      override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
        stop()
      }

      override fun onNotificationPosted(notificationId: Int, notification: Notification, ongoing: Boolean) {
        if (ongoing)
        // Make sure the service will not get destroyed while playing media.
          startForeground(notificationId, notification)
        else
        // Make notification cancellable.
          stopForeground(false)
      }
    }

    val builder = PlayerNotificationManager.Builder(context, PLAYBACK_NOTIFICATION_ID, PLAYBACK_CHANNEL_ID)
      .setMediaDescriptionAdapter(adapter)
      .setNotificationListener(listener)

    // Show lock screen controls and let apps like Google assistant manage playback.
    val session = MediaSessionCompat(context, MEDIA_SESSION_TAG).apply { isActive = true }
    notification.mediaSessionCompat = session

    // Setup notification and media session
    notification.manager = builder.build().apply {
        // Omit skip previous and next actions.
        setUseNextAction(false)
        setUsePreviousAction(false)

        // Add stop action.
        setUseStopAction(true)

        setMediaSessionToken(session.sessionToken)

        setPlayer(player)
      }

    notification.mediaSessionConnector = MediaSessionConnector(session).apply {

      val navigator = object : TimelineQueueNavigator(session) {

        override fun getMediaDescription(player: Player, windowIndex: Int): MediaDescriptionCompat {
          return MediaDescriptionCompat.Builder().apply {
            setMediaUri(audio?.uri)
            setTitle(title)
            setSubtitle(text ?: artist)
            setDescription(subtext ?: album)
            setIconBitmap(audio?.bitmap)

            val extras = Bundle()
            extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            extras.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, albumArtist)

            artwork?.let {
              val uri = Uri.parse(it)
              setIconUri(uri)
              extras.putString(MediaMetadataCompat.METADATA_KEY_ART_URI, it)
              extras.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it)
              extras.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, it)
            }

            setExtras(extras)

          }.build()
        }
      }

      setQueueNavigator(navigator)
      setPlayer(player)
    }
  }

  @MainThread
  fun playWhenReady(playWhenReady: Boolean) {
    if (playWhenReady) startProgressListener() else stopProgressListener()
    player.playWhenReady = playWhenReady
    audio?.playWhenReady = playWhenReady
  }

  @MainThread
  fun stop() {
    player.playWhenReady = false
    player.stop(true)
    audio = null
    stopProgressListener()
    stopForeground(true)
  }

  @MainThread
  fun seekTo(seconds: Double) {
    val position = seconds.toLong() * 1000
    return player.seekTo(position)
  }

  @MainThread
  private fun startProgressListener() {
    progressHandler = Handler()
    progressHandler?.post(onPlayerProgressChanged)
  }

  private fun stopProgressListener() {
    progressHandler?.removeCallbacks(onPlayerProgressChanged)
    progressHandler = null
  }

  private val onPlayerProgressChanged = object : Runnable {
    override fun run() {
      progressHandler?.postDelayed(this, 200)

      val position = max(0, player.currentPosition / 1000)
      sendEvent(AudioModule.PLAYER_PROGRESS_EVENT, position.toDouble())
    }
  }

  /**
   * Called when an error occurs. The playback state will transition to [.STATE_IDLE]
   * immediately after this method is called. The player instance can still be used, and [ ][.release] must still be called on the player should it no longer be required.
   *
   * @param error The error.
   */
  override fun onPlayerError(error: PlaybackException) {
    sendEvent(AudioModule.PLAYER_STATE_EVENT, AudioModule.PLAYER_STATE_UNKNOWN)
    audio = null
  }

  /**
   * Called when the timeline has been refreshed.
   *
   *
   * Note that if the timeline has changed then a position discontinuity may also have
   * occurred. For example, the current period index may have changed as a result of periods being
   * added or removed from the timeline. This will *not* be reported via a separate call to
   * [.onPositionDiscontinuity].
   *
   * @param timeline The latest timeline. Never null, but may be empty.
   * @param reason The [Player.TimelineChangeReason] responsible for this timeline change.
   */
  override fun onTimelineChanged(timeline: Timeline, reason: Int) {
    val duration = if (player.duration < 0) { -1 } else { player.duration / 1000 }
    sendEvent(AudioModule.PLAYER_DURATION_EVENT, duration.toDouble())
  }

  /**
   * Called when the value returned from either [.getPlayWhenReady] or [ ][.getPlaybackState] changes.
   *
   * @param playWhenReady Whether playback will proceed when ready.
   * @param playbackState The new [playback state][Player.State].
   */
  override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
    when (playbackState) {
      Player.STATE_BUFFERING -> sendEvent(AudioModule.PLAYER_STATE_EVENT, AudioModule.PLAYER_STATE_BUFFERING)
      Player.STATE_ENDED -> sendEvent(AudioModule.PLAYER_STATE_EVENT, AudioModule.PLAYER_STATE_ENDED)
      else -> {
        sendEvent(AudioModule.PLAYER_STATE_EVENT, if (playWhenReady) AudioModule.PLAYER_STATE_PLAYING else AudioModule.PLAYER_STATE_PAUSED)
      }
    }
  }

  class BitmapDownloaderTask(
    private val url: String,
    private val callback: (Bitmap) -> Unit
  ): AsyncTask<Void, Void, Bitmap?>() {

    override fun doInBackground(vararg params: Void?): Bitmap? {
      return try {
        val url = URL(url)
        BitmapFactory.decodeStream(url.openConnection().getInputStream())
      } catch (e: Exception) { null }
    }

    override fun onPostExecute(result: Bitmap?) {
      val bitmap = result ?: return
      callback(bitmap)
    }
  }

}


