import * as React from 'react';
import { StyleSheet, View, Image } from 'react-native';
import Audio, { Source, usePlaybackState } from 'react-native-audio';
import { PlayPauseButton, StopButton } from './components'

const media: Source = {
    uri: 'http://d19bhbirxx14bg.cloudfront.net/chopin-28-4-pfaul.mp3',
    title: 'Prelude in E minor Op.28 No.4',
    artwork: 'https://upload.wikimedia.org/wikipedia/commons/e/e8/Frederic_Chopin_photo.jpeg',
    artist: 'Frédéric Chopin',
};

export default function App() {
  const state = usePlaybackState();

  React.useEffect(() => {
    console.log(state);
  }, [state]);  

  return (
    <View style = { styles.container }>

        <Image 
            style = { styles.artwork } 
            source = {{ uri: media.artwork }}
        />

        <View style = { styles.controls }>

            <PlayPauseButton 
                style = { styles.control }
                onPlay = { () => Audio.play(media) }
                onPause = { Audio.pause }
            />

            <StopButton 
                style = { styles.control } 
                onStop = { Audio.stop }
            />

      </View>
    </View>
  );
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
        borderRadius: 8,
        margin: 16,
    },

    controls: {
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
    },

    control: {
        padding: 8,
    },
});
