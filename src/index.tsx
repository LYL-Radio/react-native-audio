import { NativeModules } from 'react-native';

type AudioType = {
  multiply(a: number, b: number): Promise<number>;
};

const { Audio } = NativeModules;

export default Audio as AudioType;
