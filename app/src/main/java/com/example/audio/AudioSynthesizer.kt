package com.example.audio

import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.exp

object AudioSynthesizer {
    const val SAMPLE_RATE = 22050

    fun generatePresetPCM(preset: String, durationSec: Float): ByteArray {
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val samples = ShortArray(numSamples)

        when (preset) {
            "Zen Bowl" -> {
                val freqFund = 146.83f // D3
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val decay = exp(-1.2f * t)
                    
                    val valFund = sin(2 * PI * freqFund * t)
                    val valOver1 = 0.5f * sin(2 * PI * (freqFund * 2.01f) * t)
                    val valOver2 = 0.25f * sin(2 * PI * (freqFund * 3.02f) * t)
                    val valOver3 = 0.12f * sin(2 * PI * (freqFund * 4.04f) * t)
                    
                    val mixed = (valFund + valOver1 + valOver2 + valOver3) * 0.6f * decay
                    samples[i] = (mixed * 32767).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            "Sunrise Chime" -> {
                val freqs = floatArrayOf(523.25f, 659.25f, 783.99f, 1046.50f)
                val delays = floatArrayOf(0.0f, 0.25f, 0.5f, 0.75f)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    var mixed = 0f
                    
                    for (n in freqs.indices) {
                        val dt = t - delays[n]
                        if (dt > 0) {
                            val decay = exp(-2.5f * dt)
                            val sine = sin(2 * PI * freqs[n] * dt)
                            val partial = 0.25f * sin(2 * PI * (freqs[n] * 2.71f) * dt)
                            mixed += ((sine + partial) * 0.35f * decay).toFloat()
                        }
                    }
                    samples[i] = (mixed * 32767).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            "Digital Beeps" -> {
                val beepFreq = 1200f
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val cycleTime = t % 1.0f
                    
                    val isBeep = when {
                        cycleTime in 0.0f..0.15f -> true
                        cycleTime in 0.30f..0.45f -> true
                        else -> false
                    }
                    
                    if (isBeep) {
                        val value = sin(2 * PI * beepFreq * t) * 0.5f
                        samples[i] = (value * 32767).toInt().toShort()
                    } else {
                        samples[i] = 0
                    }
                }
            }
            "Morning Forest" -> {
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    
                    val hash = ((i * 1664525) + 1013904223) and 0xFFFF
                    val noise = ((hash.toFloat() / 65535f) - 0.5f) * 0.04f
                    
                    var chirp = 0f
                    val cycle1 = t % 1.0f
                    if (cycle1 in 0.1f..0.3f) {
                        val dt = cycle1 - 0.1f
                        val freq = 2200f + (dt / 0.2f) * 1500f
                        chirp += (sin(2 * PI * freq * dt) * 0.25f * exp(-6f * dt)).toFloat()
                    }
                    
                    val cycle2 = (t + 0.5f) % 1.2f
                    if (cycle2 in 0.1f..0.25f) {
                        val dt = cycle2 - 0.1f
                        val freq = 3500f - (dt / 0.15f) * 1000f
                        chirp += (sin(2 * PI * freq * dt) * 0.25f * exp(-8f * dt)).toFloat()
                    }
                    
                    val mixed = chirp + noise
                    samples[i] = (mixed * 32767).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            "Synth Wave Beat" -> {
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val beatIndex = ((t * 4) % 4).toInt()
                    val tInBeat = (t % 0.25f)
                    
                    val baseFreq = when (beatIndex) {
                        0 -> 110.0f
                        1 -> 130.81f
                        2 -> 97.99f
                        3 -> 146.83f
                        else -> 110.0f
                    }
                    
                    val bassVal = (sin(2 * PI * baseFreq * tInBeat) + 
                                  0.4f * sin(2 * PI * (baseFreq * 2) * tInBeat) +
                                  0.2f * sin(2 * PI * (baseFreq * 3) * tInBeat)) * 0.35f * exp(-4f * tInBeat)
                    
                    var leadVal = 0f
                    if (beatIndex == 0 || beatIndex == 2) {
                        val leadFreq = if (beatIndex == 0) 440.0f else 587.33f
                        leadVal = (sin(2 * PI * leadFreq * tInBeat) * 0.25f * exp(-8f * tInBeat)).toFloat()
                    }
                    
                    val mixed = (bassVal + leadVal) * 0.7f
                    samples[i] = (mixed * 32767).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            "Cyber Alert" -> {
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val cycle = t % 0.3f
                    val currentFreq = 400f + (cycle / 0.3f) * 1400f
                    
                    val mixed = sin(2 * PI * currentFreq * cycle) * 0.45f
                    samples[i] = (mixed * 32767).toInt().toShort()
                }
            }
            "Lofi Chord" -> {
                val freqs = floatArrayOf(220.0f, 261.63f, 329.63f, 392.0f)
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val env = if (t < 0.5f) {
                        t / 0.5f
                    } else {
                        exp(-0.6f * (t - 0.5f))
                    }
                    
                    var mixed = 0f
                    for (f in freqs) {
                        mixed += sin(2 * PI * f * t).toFloat()
                    }
                    mixed = (mixed / 4.0f) * 0.5f * env
                    samples[i] = (mixed * 32767).toInt().toShort()
                }
            }
            "High Pitch" -> {
                // Piercing dual-tone alarm: 2400 Hz + 3200 Hz pulsed in rapid 0.1s on/off bursts
                val freq1 = 2400f
                val freq2 = 3200f
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    val cycleTime = t % 0.2f
                    if (cycleTime < 0.1f) {
                        val v1 = sin(2 * PI * freq1 * t) * 0.55f
                        val v2 = sin(2 * PI * freq2 * t) * 0.35f
                        samples[i] = ((v1 + v2) * 32767).toInt().coerceIn(-32768, 32767).toShort()
                    } else {
                        samples[i] = 0
                    }
                }
            }
            else -> {
                for (i in 0 until numSamples) {
                    val t = i.toFloat() / SAMPLE_RATE
                    samples[i] = (sin(2 * PI * 800 * t) * 0.5f * 32767).toInt().toShort()
                }
            }
        }

        val bytes = ByteArray(numSamples * 2)
        for (i in 0 until numSamples) {
            val s = samples[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
