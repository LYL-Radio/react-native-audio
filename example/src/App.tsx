import Audio, { Source, usePlaybackState } from '@lyl-radio/react-native-audio'
import * as React from 'react'
import { StyleSheet, View } from 'react-native'

import { Artwork, PlayPauseButton, ProgressBar, StopButton } from './components'

const media: Source = {
  uri: 'https://www.mfiles.co.uk/mp3-downloads/prelude04.mp3',
  title: 'Prelude in E minor Op.28 No.4',
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
        <StopButton style={styles.control} onStop={Audio.stop} />
      </View>
    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
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
