import { PlaybackState, usePlaybackState } from '@lyl-radio/react-native-audio'
import React from 'react'
import { Image, StyleSheet, View, ViewProps } from 'react-native'
import Spinner from 'react-native-spinkit'

type Props = {
  artwork?: string
} & ViewProps

export function Artwork(props: Props): React.ReactElement {
  const state = usePlaybackState()

  return (
    <View {...props}>
      <Image style={styles.artwork} source={{ uri: props.artwork }} />
      <View style={styles.spinner}>
        <Spinner
          isVisible={state === PlaybackState.Buffering}
          type='Wave'
          size={40}
          color='white'
        />
      </View>
    </View>
  )
}

const styles = StyleSheet.create({
  artwork: {
    ...StyleSheet.absoluteFillObject,
    borderRadius: 8,
  },

  spinner: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
})
