import React from 'react'
import { TouchableOpacity, ViewProps } from 'react-native'
import Icon from 'react-native-vector-icons/FontAwesome'

type Props = {
  onStop: () => void
} & ViewProps

export function StopButton(props: Props): React.ReactElement {
  return (
    <TouchableOpacity {...props} onPress={props.onStop}>
      <Icon name='stop' size={40} color='black' />
    </TouchableOpacity>
  )
}
