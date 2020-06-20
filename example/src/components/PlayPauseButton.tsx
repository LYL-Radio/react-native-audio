import React, { useEffect, useState, useCallback } from 'react';
import { ViewProps, TouchableOpacity } from 'react-native';
import Icon from 'react-native-vector-icons/FontAwesome';
import { PlaybackState, usePlaybackState } from '@lyl-radio/react-native-audio';

type Props = {
  onPlay: () => void;
  onPause: () => void;
} & ViewProps;

export function PlayPauseButton(props: Props): React.ReactElement {
  const [play, setPlay] = useState(false);

  const playbackState = usePlaybackState();

  useEffect(() => {
    switch (playbackState) {
      case PlaybackState.Playing:
        setPlay(true);
        break;

      case PlaybackState.Paused:
        setPlay(false);
        break;
    }
  }, [playbackState]);

  const toggle = useCallback(() => {
    if (play) {
      setPlay(false);
      props.onPause();
    } else {
      setPlay(true);
      props.onPlay();
    }
  }, [play, props]);

  return (
    <TouchableOpacity {...props} onPress={toggle}>
      <Icon name={play ? 'pause' : 'play'} size={40} color="black" />
    </TouchableOpacity>
  );
}
