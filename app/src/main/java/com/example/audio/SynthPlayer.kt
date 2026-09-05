package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

object SynthPlayer {
    private var audioTrack: AudioTrack? = null
    private var mediaPlayer: MediaPlayer? = null
    private var playScope: CoroutineScope? = null
    private val isPlaying = AtomicBoolean(false)

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    @Suppress("DEPRECATION")
    private val legacyFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) stop()
    }

    /** Play either a synth preset or a custom file URI (prefixed with "file://" or "content://"). */
    fun playPreset(context: Context, presetName: String, loop: Boolean, volume: Float = 0.8f) {
        stop()

        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = am
        requestAudioFocus(am)

        // If it looks like a URI, play it with MediaPlayer instead of the synth engine
        if (presetName.startsWith("content://") || presetName.startsWith("file://")) {
            playUri(context, presetName, loop, volume)
        } else {
            playSynth(presetName, loop, volume)
        }
    }

    private fun requestAudioFocus(am: AudioManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener(legacyFocusListener)
                .build()
            focusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(legacyFocusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun playUri(context: Context, uriString: String, loop: Boolean, volume: Float) {
        try {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                setDataSource(context, Uri.parse(uriString))
                isLooping = loop
                setVolume(volume, volume)
                prepare()
                start()
            }
            mediaPlayer = mp
            isPlaying.set(true)
        } catch (e: Exception) {
            Log.e("SynthPlayer", "Error playing URI $uriString", e)
        }
    }

    private fun playSynth(presetName: String, loop: Boolean, volume: Float) {
        isPlaying.set(true)
        playScope = CoroutineScope(Dispatchers.Default + Job())
        playScope?.launch {
            try {
                val pcmData = AudioSynthesizer.generatePresetPCM(presetName, 2.0f)
                val bufferSize = AudioTrack.getMinBufferSize(
                    AudioSynthesizer.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(pcmData.size)

                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(AudioSynthesizer.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                audioTrack = track
                track.setVolume(volume)
                track.play()

                while (isPlaying.get() && isActive) {
                    track.write(pcmData, 0, pcmData.size)
                    if (!loop) {
                        val playTimeMs = (pcmData.size / 2.0f / AudioSynthesizer.SAMPLE_RATE * 1000).toLong()
                        delay(playTimeMs)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("SynthPlayer", "Error playing preset $presetName", e)
            } finally {
                if (!loop) cleanUpAudioTrack()
            }
        }
    }

    fun stop() {
        isPlaying.set(false)
        playScope?.cancel()
        playScope = null
        cleanUpAudioTrack()
        cleanUpMediaPlayer()
        abandonAudioFocus()
    }

    private fun cleanUpAudioTrack() {
        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    stop(); release()
                }
            }
        } catch (e: Exception) {
            Log.e("SynthPlayer", "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }

    private fun cleanUpMediaPlayer() {
        try {
            mediaPlayer?.apply { if (isPlaying) stop(); release() }
        } catch (e: Exception) {
            Log.e("SynthPlayer", "Error releasing MediaPlayer", e)
        } finally {
            mediaPlayer = null
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
                focusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(legacyFocusListener)
            }
        } catch (e: Exception) {
            Log.e("SynthPlayer", "Error abandoning audio focus", e)
        }
        audioManager = null
    }
}
