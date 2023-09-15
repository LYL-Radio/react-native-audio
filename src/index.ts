import { useEffect, useState } from 'react'
import {
  DeviceEventEmitter,
  EmitterSubscription,
  NativeEventEmitter,
  NativeModules,
  Platform,
} from 'react-native'
// @ts-ignore
import resolveAssetSource from 'react-native/Libraries/Image/resolveAssetSource'

const { Audio } = NativeModules
const emitter =
  Platform.OS === 'ios' ? new NativeEventEmitter(Audio) : DeviceEventEmitter

// MARK: - Helpers

function resolveAsset(source?: number | string) {
  if (!source) return undefined
  return resolveAssetSource(source)?.uri || source
}

// MARK: - Audio Controls

const play = (source?: Source): Promise<void> => {
  if (!source) return Audio.play()
  source = { ...source }

  source.uri = resolveAsset(source.uri)
  source.artwork = resolveAsset(source.artwork)

  return Audio.source(source).then(() => Audio.play())
}

const seekTo = (position: number): Promise<void> => Audio.seekTo(position)
const pause = (): Promise<void> => Audio.pause()
const stop = (): Promise<void> => Audio.stop()

// MARK: - Data

export enum PlaybackState {
  Unknown = Audio.PLAYER_STATE_UNKNOWN,
  Buffering = Audio.PLAYER_STATE_BUFFERING,
  Paused = Audio.PLAYER_STATE_PAUSED,
  Playing = Audio.PLAYER_STATE_PLAYING,
  Ended = Audio.PLAYER_STATE_ENDED,
}

export enum PlayerEvent {
  PlaybackState = 'player-state', // eslint-disable-line @typescript-eslint/no-shadow
  Progress = 'player-progress',
  Duration = 'player-duration',
}

export type Source = {
  uri: string
  title: string
  artwork?: string
  album?: string
  artist?: string
  albumArtist?: string
}

/**
 * Adds a listener to be invoked when player events are emitted.
 * An optional calling context may be provided. The data arguments
 * emitted will be passed to the listener function.
 *
 * @param event - The player event to listen to
 * @param listener - Function to invoke when the specified event is
 *   emitted
 * @param context - Optional context object to use when invoking the
 *   listener
 */
const addListener = (
  event: PlayerEvent,
  listener: (...args: any[]) => any,
  context?: any
): EmitterSubscription => {
  return emitter.addListener(event, listener, context)
}

/**
 * @description
 *   Get playback state
 */
export const usePlaybackState = (): PlaybackState => {
  const [state, setState] = useState(PlaybackState.Unknown)

  useEffect(() => {
    const subscription = addListener(PlayerEvent.PlaybackState, setState)
    return () => subscription.remove()
  }, [])

  return state
}

/**
 * @description
 *   Get player duration
 */
export const usePlayerDuration = (): number => {
  const [duration, setDuration] = useState(-1)

  useEffect(() => {
    const subscription = addListener(PlayerEvent.Duration, setDuration)
    return () => subscription.remove()
  }, [])

  return duration
}

/**
 * @description
 *   Get player progress
 */
export const usePlayerProgress = (): number => {
  const [progress, setProgress] = useState(0)

  useEffect(() => {
    const subscription = addListener(PlayerEvent.Progress, setProgress)
    return () => subscription.remove()
  }, [])

  return progress
}

export default {
  addListener,
  play,
  seekTo,
  pause,
  stop,
}
