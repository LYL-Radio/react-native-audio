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

export enum State {
  Unknown = 'unknown',
  Buffering = 'buffering',
  Paused = 'paused',
  Playing = 'playing',
}

export enum Event {
  PlaybackState = 'player-state',
  Progress = 'player-progress',
}

export type Source = {
  uri: string;
  title: string;
  description?: string;
  artwork?: string;
};

/**
 * @description
 *   Get playback state
 */
export const usePlaybackState = (): State => {
  const [state, setState] = useState(State.Unknown);

  useEffect(() => {
    const subscription = emitter.addListener(Event.PlaybackState, setState);
    return () => subscription.remove();
  }, []);

  return state;
};

export default {
  registerAudioService,
  play,
  pause,
  resume,
  stop,
};
