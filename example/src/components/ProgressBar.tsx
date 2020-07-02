import React, { useState, useCallback } from 'react';
import { View, Text, StyleSheet, ViewProps } from 'react-native';
import Slider from '@react-native-community/slider';
import moment from 'moment';
import { usePlayerProgress, usePlayerDuration } from '@lyl-radio/react-native-audio';

type Props = {
  onSeek?: (position: number) => void;
} & ViewProps;

const format = (seconds: number): string => {
  const duration = moment.duration({ seconds: seconds });
  const ms = duration.asMilliseconds();
  return moment.utc(ms).format('mm:ss');
};

export function ProgressBar(props: Props): React.ReactElement {
  const duration = usePlayerDuration();
  const progress = usePlayerProgress();
  const [isSliding, setIsSliding] = useState(false);

  const elapsed = format(progress);
  const remaining = format(duration - progress);

  const onValueChange = useCallback(
    (value: number) => {
      if (isSliding && props.onSeek) {
        props.onSeek(value);
      }
    },
    [isSliding, props]
  );

  const onSlidingStart = useCallback(
    (value: number) => {
      setIsSliding(true);
      if (props.onSeek) {
        props.onSeek(value);
      }
    },
    [props]
  );

  const onSlidingComplete = useCallback(
    (value: number) => {
      setIsSliding(false);
      if (props.onSeek) {
        props.onSeek(value);
      }
    },
    [props]
  );

  const value = isSliding ? undefined : progress;

  return (
    <View {...props}>
      <Slider
        maximumValue={duration}
        onSlidingStart={onSlidingStart}
        onSlidingComplete={onSlidingComplete}
        onValueChange={onValueChange}
        value={value}
        minimumTrackTintColor="black"
        maximumTrackTintColor="lightgray"
      />
      <View style={styles.timeline}>
        <Text style={styles.text}>{elapsed}</Text>
        <View style={styles.spacer} />
        <Text style={styles.text}>{'-' + remaining}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  timeline: {
    flexDirection: 'row',
  },

  spacer: {
    flex: 1,
  },

  text: {
    color: 'black',
    fontSize: 12,
    textAlign: 'center',
  },
});
