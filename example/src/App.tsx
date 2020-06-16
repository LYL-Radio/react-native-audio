import * as React from 'react';
import { StyleSheet, View, TouchableOpacity, Image } from 'react-native';
import Icon from 'react-native-vector-icons/FontAwesome';
import Audio, { Source, usePlaybackState } from 'react-native-audio';

export default function App() {

  const state = usePlaybackState()

  React.useEffect(() => {
    console.log(state)
  }, [state])

  // 'http://icecast.lyl.live/live'

  const media: Source = {
    uri: 'http://d19bhbirxx14bg.cloudfront.net/chopin-28-4-pfaul.mp3',
    title: 'Prelude in E minor Op.28 No.4',
    artwork: 'https://upload.wikimedia.org/wikipedia/commons/e/e8/Frederic_Chopin_photo.jpeg',
    description: 'Frédéric Chopin',
  }

  return (
    <View style={styles.container}>
      <Image style={styles.artwork} source = { require('./chopin.png') }/>
      <View style={styles.controls}>
        <TouchableOpacity style = { styles.control } onPress = { () => { Audio.play(media) } }>
            <Icon name = { 'play' } size = { 30 }/>
        </TouchableOpacity>
        <TouchableOpacity style = { styles.control } onPress = { () => { Audio.pause() } }>
            <Icon name = { 'pause' } size = { 30 }/>
        </TouchableOpacity>
        <TouchableOpacity style = { styles.control } onPress = { () => { Audio.stop() } }>
            <Icon name = { 'stop' } size = { 30 }/>
        </TouchableOpacity>
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
