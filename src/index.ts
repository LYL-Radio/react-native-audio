import { useState, useEffect } from 'react';
import {
  AppRegistry,
  NativeModules,
  Platform,
  NativeEventEmitter,
  DeviceEventEmitter,
  TaskProvider,
} from 'react-native';
// @ts-ignore
import resolveAssetSource from 'react-native/Libraries/Image/resolveAssetSource';

const { Audio } = NativeModules;
const emitter =
  Platform.OS === 'ios' ? new NativeEventEmitter(Audio) : DeviceEventEmitter;

// MARK: - Helpers

function resolveAsset(source?: number | string) {
  if (!source) return undefined;
  return resolveAssetSource(source)?.uri || source;
}

function registerAudioService(task: TaskProvider) {
  if (Platform.OS === 'android') {
    // Registers the headless task
    AppRegistry.registerHeadlessTask('react-native-audio', task);
  } else {
    // Initializes and runs the service in the next tick
    setImmediate(task());
  }
}

// MARK: - Audio Controls

const play = (source: Source): Promise<void> => {
  source = { ...source };

  source.uri = resolveAsset(source.uri);
  source.artwork = resolveAsset(source.artwork);

  return Audio.play(source);
};

const pause = (): Promise<void> => Audio.pause();
const resume = (): Promise<void> => Audio.resume();
const stop = (): Promise<void> => Audio.stop();

// MARK: - Data

export enum PlaybackState {
  Unknown = Audio.PLAYER_STATE_UNKNOWN,
  Buffering = Audio.PLAYER_STATE_BUFFERING,
  Paused = Audio.PLAYER_STATE_PAUSED,
  Playing = Audio.PLAYER_STATE_PLAYING,
  Ended = Audio.PLAYER_STATE_ENDED,
}

export enum PlayerEvent {
  PlaybackState = 'player-state',
  Progress = 'player-progress',
}

export type Source = {
  uri: string;
  title: string;
  artwork?: string;
  album?: string;
  artist?: string;
  albumArtist?: string;
  text?: string; // Android only
  subtext?: string; // Android only
};

/**
 * @description
 *   Get playback state
 */
export const usePlaybackState = (): PlaybackState => {
  const [state, setState] = useState(PlaybackState.Unknown);

  useEffect(() => {
    const subscription = emitter.addListener(
      PlayerEvent.PlaybackState,
      setState
    );
    return () => subscription.remove();
  }, []);

  return state;
};

/**
 * @description
 *   Get player progress
 */
export const usePlayerProgress = (): number => {
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    const subscription = emitter.addListener(PlayerEvent.Progress, setProgress);
    return () => subscription.remove();
  }, []);

  return progress;
};

export default {
  registerAudioService,
  play,
  pause,
  resume,
  stop,
};
