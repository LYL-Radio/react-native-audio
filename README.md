# react-native-audio

React Native audio library with background capabilities on both iOS and Android.

## Installation

Install from the command line:
```
$ npm install @lyl-radio/react-native-audio@0.6.5
```

Install via package.json:
```
"@lyl-radio/react-native-audio": "0.6.5"
```

## Usage

The `Audio` takes a [`Source`](src/index.ts#L67) object as input.

```js
import Audio from "@lyl-radio/react-native-audio"

Audio.play({ uri: 'https://your/content.mp3' })
```

> See [example](example) for more details.

### Hooks
 
A simple set of hooks allows you to respond to player events.

### `usePlaybackState`

The player state hook will return [PlaybackState](src/index.ts#L53) on change events allowing you to update your stateful function component when the player starts `playing`, `buffering` or on `paused` or `ended`.

### `usePlayerDuration`

The player duration hook will update your function component when the player has updated the playing track duration in seconds.

### `usePlayerProgress`

Updates your function component as the player progress. The progress value is expressed in seconds.

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

[MIT](LICENSE)
