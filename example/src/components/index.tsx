import React, { useEffect, useState } from 'react';
import { ViewProps, TouchableOpacity, StyleSheet } from 'react-native';
import Icon from 'react-native-vector-icons/FontAwesome';
import Spinner from 'react-native-spinkit';
import { PlaybackState, usePlaybackState } from 'react-native-audio';

type PlayPauseButtonProps = {
    onPlay: () => void,
    onPause: () => void,
} & ViewProps

export function PlayPauseButton(props: PlayPauseButtonProps): React.ReactElement {

    const [state, setState] = useState({ play: false, buffering: false })

    const playbackState = usePlaybackState();

    useEffect(() => {

        switch (playbackState) {

            case PlaybackState.Playing:
                setState({ play: true, buffering: false })
                break;

            case PlaybackState.Buffering:
                setState({ ...state, buffering: true })
                break;
        
            default:
                setState({ play: false, buffering: false })
                break;
        }

    }, [playbackState])

    useEffect(() => {
        state.play ? props.onPlay() : props.onPause()
    }, [state.play])

    const toggle = () => {
        const play = !state.play
        setState({ ...state, play: play })
    }

    const icon = () => {
        if (state.buffering) {
            return (
                <Spinner
                    isVisible = { state.buffering } 
                    type = { 'Wave' }
                    size = { styles.icon.width } 
                    color = { styles.icon.color } 
                />
            )
        }

        return (
            <Icon 
                style = { styles.icon } 
                name = { state.play ? 'pause' : 'play' } 
                size = { styles.icon.width } 
                color = { styles.icon.color } 
            />
        )
    }

    return (
        <TouchableOpacity { ...props } onPress = { toggle }>
            { icon() }
        </TouchableOpacity>
    )
}

type StopButtonProps = {
    onStop: () => void,
} & ViewProps

export function StopButton(props: StopButtonProps): React.ReactElement {

    return (
        <TouchableOpacity { ...props } onPress = { props.onStop }>
            <Icon 
                style = { styles.icon } 
                name = { 'stop' } 
                size = { styles.icon.width } 
                color = { styles.icon.color } 
            />
        </TouchableOpacity>
    )
}

const styles = StyleSheet.create({

    icon: {
        width: 40,
        color: 'black',
    },
});