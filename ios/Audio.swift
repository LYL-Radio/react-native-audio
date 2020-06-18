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

    public override var methodQueue: DispatchQueue { queue }

    public override func supportedEvents() -> [String] {
        return [
            Audio.PlayerStateEvent,
            Audio.PlayerProgressEvent
        ]
    }

    override func constantsToExport() -> [AnyHashable : Any] {
        return [
            "PLAYER_STATE_EVENT": Audio.PlayerStateEvent,
            "PLAYER_PROGRESS_EVENT": Audio.PlayerProgressEvent,

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
            guard let player = self?.player, player.rate == 0 else { return .commandFailed }
            player.play()
            return .success
        }

        // Add handler for Pause Command
        cmd.pauseCommand.addTarget { [weak self] event in
            guard let player = self?.player, player.rate == 1 else { return .commandFailed }
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
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    private func reset() {
        player = nil
        statusObserver = nil
        timeControlStatusObserver = nil
        timeObserverToken = nil
        source = nil

        DispatchQueue.main.sync {
            let session = AVAudioSession.sharedInstance()
            try? session.setActive(false)
            UIApplication.shared.endReceivingRemoteControlEvents()
        }
    }

    @objc private func playerDidPlayToEnd(_ notification: Notification) {
        sendEvent(withName: Audio.PlayerStateEvent, body: Audio.PlayerStateEnded)
    }

    @objc private func audioSessionInterruption(_ notification: Notification) {
        player?.pause()
    }

    private func downloadArtwork(completion: @escaping () -> Void) {
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

        DispatchQueue.main.sync {
            let session = AVAudioSession.sharedInstance()
            try? session.setCategory(.playback, mode: .default)
            try? session.setActive(true)
            UIApplication.shared.beginReceivingRemoteControlEvents()
        }

        self.source = Source(uri: uri, metadata: data, artwork: nil)

        let player = AVPlayer(url: uri)

        statusObserver = player.observe(\.status) { [weak self] player, _ in
            switch player.status {
            case .readyToPlay: self?.updateNowPlaying(); resolve(nil)
            case .failed: reject("audio_play", "Audio failed to load", player.error)
            default: break
            }
        }

        timeControlStatusObserver = player.observe(\.timeControlStatus) { [weak self] player, change in
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
        let interval = CMTime(seconds: 0.25, preferredTimescale: CMTimeScale(NSEC_PER_SEC))

        // Add time observer. Invoke closure on the queue.
        timeObserverToken = player.addPeriodicTimeObserver(forInterval: interval, queue: queue) { [weak self] time in
            self?.sendEvent(withName: Audio.PlayerProgressEvent, body: CMTimeGetSeconds(time))
        }

        self.player = player
        player.play()
    }

    private func updateNowPlaying() {
        guard let source = source else { return }

        // Get the shared MPRemoteCommandCenter
        let cmd = MPRemoteCommandCenter.shared()

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
            downloadArtwork(completion: updateNowPlaying)
        }

        if let player = player {
            info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = player.currentTime().seconds
            info[MPMediaItemPropertyPlaybackDuration] = player.currentItem?.asset.duration.seconds
            info[MPNowPlayingInfoPropertyPlaybackRate] = player.rate
        }

        if let duration = player?.currentItem?.duration {
            let isLive = CMTIME_IS_INDEFINITE(duration)
            info[MPNowPlayingInfoPropertyIsLiveStream] = isLive
            cmd.changePlaybackPositionCommand.isEnabled = !isLive
        }

        // Set the metadata
        center.nowPlayingInfo = info
    }

    @objc(update:resolver:rejecter:)
    public func update(metadata: [String: Any], resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        source?.metadata = metadata
        updateNowPlaying()
        resolve(nil)
    }

    @objc(resume:rejecter:)
    public func resume(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        if let player = player, player.rate == 0 {
            player.play()
        }
        resolve(nil)
    }

    @objc(pause:rejecter:)
    public func pause(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        if let player = player, player.rate == 1 {
            player.pause()
        }
        resolve(nil)
    }

    @objc(stop:rejecter:)
    public func stop(resolve: RCTPromiseResolveBlock, reject: RCTPromiseRejectBlock) {
        player?.pause()
        reset()
        resolve(nil)
    }
}
