import Audio, { Source, usePlaybackState } from '@lyl-radio/react-native-audio'
import * as React from 'react'
import { StyleSheet, View } from 'react-native'

import { Artwork, PlayPauseButton, ProgressBar, StopButton } from './components'

const media: Source = {
  uri: 'https://upload.wikimedia.org/wikipedia/commons/a/a0/Allegro_de_Concert_Op._46_in_A_Major.mp3',
  title: 'Allegro de concert, Op. 46',
  artwork:
    'https://upload.wikimedia.org/wikipedia/commons/e/e8/Frederic_Chopin_photo.jpeg',
  artist: 'Frédéric Chopin',
}

export default function App(): React.ReactElement {
  const state = usePlaybackState()

  React.useEffect(() => {
    console.log(state)
  }, [state])

  return (
    <View style={styles.container}>
      <Artwork style={styles.artwork} artwork={media.artwork} />

      <ProgressBar
        style={styles.progress}
        onSeek={(position) => Audio.seekTo(position)}
      />

      <View style={styles.controls}>
        <PlayPauseButton
          style={styles.control}
          onPlay={() => Audio.play(media)}
          onPause={() => Audio.pause()}
        />
        <StopButton
         style={styles.control} 
         onStop={() => Audio.stop()} />
      </View>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'white'
  },

  artwork: {
    width: 150,
    height: 150,
    margin: 16,
    borderRadius: 8,
  },

  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
  },

  progress: {
    width: '100%',
    paddingHorizontal: 32,
  },

  control: {
    padding: 8,
  },
})
