# react-native-audio

React Native audio library

## Installation

This package is available from [GitHub Packages](https://github.com/features/packages):

1. In the same directory as your package.json file, create or edit an `.npmrc` file to include a line specifying GitHub Packages URL and this org:
   ```
   @lyl-radio:registry=https://npm.pkg.github.com
   ```
2. Install from the command line:
    ```sh
    npm install @lyl-radio/react-native-audio
    ```

> You can find more details in [Github Help](https://help.github.com/en/packages/using-github-packages-with-your-projects-ecosystem/configuring-npm-for-use-with-github-packages#installing-a-package)

## Usage

```js
import Audio from "@lyl-radio/react-native-audio"

// ...

Audio.play({uri: 'https://your/content.mp3'})
```

## Contributing

See the [contributing guide](CONTRIBUTING.md) to learn how to contribute to the repository and the development workflow.

## License

[MIT](LICENSE)
