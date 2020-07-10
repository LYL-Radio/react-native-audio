import Foundation
import AVFoundation
import MediaPlayer

@objc(Audio)
class Audio: RCTEventEmitter {

    struct Source {
        let uri: URL
        var metadata: [String: Any]
        var artwork: MPMediaItemArtwork?
    }

    // Events
    static let PlayerStateEvent = "player-state"
    static let PlayerProgressEvent = "player-progress"
    static let PlayerDurationEvent = "player-duration"

    // Player States
    static let PlayerStatePlaying = "playing"
    static let PlayerStatePaused = "paused"
    static let PlayerStateBuffering = "buffering"
    static let PlayerStateEnded = "ended"
    static let PlayerStateUnknown = "unknown"

    private let queue: DispatchQueue

    private var player: AVPlayer?
    private var source: Source?

    // Observers
    private var statusObserver: NSKeyValueObservation?
    private var timeControlStatusObserver: NSKeyValueObservation?
    private var timeObserverToken: Any?

    public override class func requiresMainQueueSetup() -> Bool { false }

    /// The queue that will be used to call all exported methods.
    public override var methodQueue: DispatchQueue { queue }

    /// The list of events emitted ny the module
    public override func supportedEvents() -> [String] {
        return [
            Audio.PlayerStateEvent,
            Audio.PlayerProgressEvent,
            Audio.PlayerDurationEvent
        ]
    }

    /// Injects constants into JS. These constants are made accessible via NativeModules.ModuleName.X. It is only called once
    /// for the lifetime of the bridge, so it is not suitable for returning dynamic values, but may be used for long-lived
    /// values such as session keys, that are regenerated only as part of a reload of the entire React application.
    ///
    /// - Returns: The list of constans to export.
    override func constantsToExport() -> [AnyHashable : Any] {
        return [
            "PLAYER_STATE_EVENT": Audio.PlayerStateEvent,
            "PLAYER_PROGRESS_EVENT": Audio.PlayerProgressEvent,
            "PLAYER_DURATION_EVENT": Audio.PlayerDurationEvent,

            "PLAYER_STATE_PLAYING": Audio.PlayerStatePlaying,
            "PLAYER_STATE_PAUSED": Audio.PlayerStatePaused,
            "PLAYER_STATE_BUFFERING": Audio.PlayerStateBuffering,
            "PLAYER_STATE_ENDED": Audio.PlayerStateEnded,
            "PLAYER_STATE_UNKNOWN": Audio.PlayerStateUnknown,
        ]
    }

    public override init() {
        queue = DispatchQueue(label: "audio.serial.queue")

        super.init()

        // Get the shared MPRemoteCommandCenter
        let cmd = MPRemoteCommandCenter.shared()

        cmd.pauseCommand.isEnabled = true
        cmd.playCommand.isEnabled = true
        cmd.changePlaybackPositionCommand.isEnabled = false
        cmd.stopCommand.isEnabled = false
        cmd.togglePlayPauseCommand.isEnabled = false
        cmd.nextTrackCommand.isEnabled = false
        cmd.previousTrackCommand.isEnabled = false
        cmd.changeRepeatModeCommand.isEnabled = false
        cmd.changeShuffleModeCommand.isEnabled = false
        cmd.changePlaybackRateCommand.isEnabled = false
        cmd.seekBackwardCommand.isEnabled = false
        cmd.seekForwardCommand.isEnabled = false
        cmd.skipBackwardCommand.isEnabled = false
        cmd.skipForwardCommand.isEnabled = false
        cmd.ratingCommand.isEnabled = false
        cmd.likeCommand.isEnabled = false
        cmd.dislikeCommand.isEnabled = false
        cmd.bookmarkCommand.isEnabled = false
        cmd.enableLanguageOptionCommand.isEnabled = false
        cmd.disableLanguageOptionCommand.isEnabled = false

        // Add handler for Play Command
        cmd.playCommand.addTarget { [weak self] event in
            guard let player = self?.player else { return .commandFailed }
            player.play()
            return .success
        }

        // Add handler for Pause Command
        cmd.pauseCommand.addTarget { [weak self] event in
            guard let player = self?.player else { return .commandFailed }
            player.pause()
            return .success
        }

        cmd.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let player = self?.player, let event = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let time = CMTime(seconds: event.positionTime, preferredTimescale: CMTimeScale(NSEC_PER_SEC))
            player.seek(to: time)
            return .success
        }

        NotificationCenter.default.addObserver(self,
                                               selector: #selector(playerDidPlayToEnd(_:)),
                                               name: .AVPlayerItemDidPlayToEndTime,
                                               object: nil)

        NotificationCenter.default.addObserver(self,
                                               selector: #selector(audioSessionInterruption(_:)),
                                               name: AVAudioSession.interruptionNotification,
                                               object: nil)

        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default)
        try? session.setActive(true)
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    @objc private func playerDidPlayToEnd(_ notification: Notification) {
        sendEvent(withName: Audio.PlayerStateEvent, body: Audio.PlayerStateEnded)
    }

    @objc private func audioSessionInterruption(_ notification: Notification) {
        guard
            let userInfo = notification.userInfo,
            let typeValue = userInfo[AVAudioSessionInterruptionTypeKey] as? UInt,
            let type = AVAudioSession.InterruptionType(rawValue: typeValue)
        else { return }

        switch type {

        case .began:
            player?.pause()

        case .ended:
            guard let optionsValue = userInfo[AVAudioSessionInterruptionOptionKey] as? UInt else { return }
            let options = AVAudioSession.InterruptionOptions(rawValue: optionsValue)
            if options.contains(.shouldResume) { player?.play() }
        @unknown default:
            break
        }
    }

    private func reset() {

        if let observer = timeObserverToken {
            player?.removeTimeObserver(observer)
        }

        statusObserver = nil
        timeControlStatusObserver = nil
        timeObserverToken = nil
        source = nil
        player = nil
    }

    // Module CMD

    @objc(play:resolver:rejecter:)
    public func play(data: [String: Any], resolve: @escaping RCTPromiseResolveBlock, reject: @escaping RCTPromiseRejectBlock) {

        guard
            let str = data["uri"] as? String,
            let uri = URL(string: str)
        else { return reject("audio_play", "Invalid track URL", nil) }

        if source?.uri == uri {
            return resume(resolve: resolve, reject: reject)
        }

        // reset current player
        reset()

        source = Source(uri: uri, metadata: data, artwork: nil)

        let player = AVPlayer(url: uri)

        DispatchQueue.main.sync {
            UIApplication.shared.beginReceivingRemoteControlEvents()
        }

        statusObserver = player.observe(\.status) { [weak self] player, change in
            self?.setNowPlayingPlaybackInfo()

            switch player.status {
            case .readyToPlay: resolve(nil)
            case .failed: reject("audio_play", "Audio failed to load", player.error)
            default: break
            }
        }

        timeControlStatusObserver = player.observe(\.timeControlStatus) { [weak self] player, change in
            self?.setNowPlayingPlaybackInfo()

            switch player.timeControlStatus {
            case .paused:
                self?.sendEvent(withName: Audio.PlayerStateEvent, body: Audio.PlayerStatePaused)
            case .waitingToPlayAtSpecifiedRate:
                self?.sendEvent(withName: Audio.PlayerStateEvent, body: Audio.PlayerStateBuffering)
            case .playing:
                self?.sendEvent(withName: Audio.PlayerStateEvent, body: Audio.PlayerStatePlaying)
            @unknown default:
                self?.sendEvent(withName: Audio.PlayerStateEvent, body: Audio.PlayerStateUnknown)
            }
        }

        // Invoke callback every quarter seconds
        let interval = CMTime(seconds: 0.25, preferredTimescale: CMTimeScale(USEC_PER_SEC))

        // Add time observer. Invoke closure on the queue.
        timeObserverToken = player.addPeriodicTimeObserver(forInterval: interval, queue: queue) { [weak self] time in
            self?.sendEvent(withName: Audio.PlayerProgressEvent, body: CMTimeGetSeconds(time))
        }

        self.player = player
        player.play()

        setNowPlayingMetadata()
    }

    @objc(update:resolver:rejecter:)
    public func update(metadata: [String: Any], resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        source?.metadata = metadata
        setNowPlayingMetadata()
        resolve(nil)
    }

    @objc(resume:rejecter:)
    public func resume(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        player?.play()
        resolve(nil)
    }

    @objc(pause:rejecter:)
    public func pause(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        player?.pause()
        resolve(nil)
    }

    @objc(seekTo:resolver:rejecter:)
    public func seekTo(position: Double, resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        let time = CMTime(seconds: position, preferredTimescale: CMTimeScale(USEC_PER_SEC))
        player?.seek(to: time)
        resolve(nil)
    }

    @objc(stop:rejecter:)
    public func stop(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        player?.pause()
        reset()
        
        DispatchQueue.main.sync {
            UIApplication.shared.endReceivingRemoteControlEvents()
        }
        resolve(nil)
    }
}

private extension Audio {

    func setNowPlayingMetadata() {
        guard let source = source else { return }

        let center = MPNowPlayingInfoCenter.default()

        // Define Now Playing Info
        var info: [String: Any] = [:]

        info[MPNowPlayingInfoPropertyMediaType] = MPNowPlayingInfoMediaType.audio.rawValue
        info[MPNowPlayingInfoPropertyAssetURL] = source.uri
        info[MPMediaItemPropertyTitle] = source.metadata["title"]
        info[MPMediaItemPropertyAlbumTitle] = source.metadata["album"]
        info[MPMediaItemPropertyArtist] = source.metadata["artist"]
        info[MPMediaItemPropertyAlbumArtist] = source.metadata["albumArtist"]

        if let artwork = source.artwork {
            info[MPMediaItemPropertyArtwork] = artwork
        } else {
            downloadArtwork(completion: setNowPlayingMetadata)
        }

        // Set the metadata
        center.nowPlayingInfo = info
    }

    func setNowPlayingPlaybackInfo() {

        // Get the shared MPRemoteCommandCenter
        let cmd = MPRemoteCommandCenter.shared()

        let center = MPNowPlayingInfoCenter.default()

        // Define Now Playing Info
        var info: [String: Any] = center.nowPlayingInfo ?? [:]

        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = player?.currentTime().seconds
        info[MPMediaItemPropertyPlaybackDuration] = player?.currentItem?.asset.duration.seconds
        info[MPNowPlayingInfoPropertyPlaybackRate] = player?.rate
        info[MPNowPlayingInfoPropertyDefaultPlaybackRate] = 1.0

        let duration = player?.currentItem?.duration ?? CMTime.invalid
        let isLive = duration.isIndefinite
        info[MPNowPlayingInfoPropertyIsLiveStream] = isLive
        cmd.changePlaybackPositionCommand.isEnabled = !isLive

        if let duration = player?.currentItem?.asset.duration, duration.isValid {
            sendEvent(withName: Audio.PlayerDurationEvent, body: duration.seconds)
        } else {
            sendEvent(withName: Audio.PlayerDurationEvent, body: -1)
        }

        // Set the metadata
        center.nowPlayingInfo = info
    }

    func downloadArtwork(completion: @escaping () -> Void) {
        guard
            let artwork = source?.metadata["artwork"] as? String,
            let url = URL(string: artwork)
        else { return }

        DispatchQueue.global(qos: .background).async { [weak self] in
            guard let data = try? Data(contentsOf: url), let image = UIImage(data: data) else { return }
            let artwork = MPMediaItemArtwork(boundsSize: image.size) { size in image }
            self?.source?.artwork = artwork
            completion()
        }
    }
}
