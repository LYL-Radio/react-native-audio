import React, { useState, useCallback } from 'react';
import { View, Text, StyleSheet, ViewProps } from 'react-native';
import Slider from '@react-native-community/slider';
import moment from 'moment';
import { usePlayerProgress, usePlayerDuraton } from 'react-native-audio';

type Props = {
  onSeek?: (position: number) => void;
} & ViewProps;

const format = (seconds: number): string => {
  const duration = moment.duration({ seconds: seconds });
  const ms = duration.asMilliseconds();
  return moment.utc(ms).format('mm:ss');
};

export function ProgressBar(props: Props): React.ReactElement {
  const duration = usePlayerDuraton();
  const progress = usePlayerProgress();
  const [isSliding, setIsSliding] = useState(false);

  const elapsed = format(progress);
  const remaining = format(duration - progress);

  const onValueChange = useCallback((value: number) => {
    if (isSliding && props.onSeek) {
      props.onSeek(value)
    }
  }, [isSliding, props])

  const onSlidingStart = useCallback((value: number) => {
    setIsSliding(true)
    if (props.onSeek) {
      props.onSeek(value)
    }
  }, [props])

  const onSlidingComplete = useCallback((value: number) => {
    setIsSliding(false)
    if (props.onSeek) {
      props.onSeek(value)
    }
  }, [props])

  const value = isSliding ? undefined : progress

  return (
    <View {...props} style={styles.container}>
      <View style={{ flexDirection: 'row' }}>
        <Text style={styles.text}>{elapsed}</Text>
        <View style={{ flex: 1 }} />
        <Text style={[styles.text, { width: 40 }]}>{'-' + remaining}</Text>
      </View>
      <Slider
        maximumValue={duration}
        onSlidingStart={onSlidingStart}
        onSlidingComplete={onSlidingComplete}
        onValueChange={onValueChange}
        value={value}
        minimumTrackTintColor="black"
        maximumTrackTintColor="lightgray"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  slider: {
    marginTop: -12,
  },
  container: {
    width: '100%',
    paddingLeft: 16,
    paddingRight: 16,
    paddingTop: 16,
  },
  track: {
    height: 2,
    borderRadius: 1,
  },
  thumb: {
    width: 10,
    height: 10,
    borderRadius: 5,
    backgroundColor: 'black',
  },
  text: {
    color: 'black',
    fontSize: 12,
    textAlign: 'center',
  },
});
