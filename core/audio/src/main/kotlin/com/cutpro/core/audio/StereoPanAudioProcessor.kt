package com.cutpro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class StereoPanAudioProcessor(
    initialPan: Float = 0.0f
) : BaseAudioProcessor() {

    var pan: Float = initialPan.coerceIn(-1.0f, 1.0f)
        private set

    private var leftGain: Float = 0.7071f
    private var rightGain: Float = 0.7071f
    private var isMonoInput: Boolean = false

    init {
        updateGains()
    }

    fun setPan(newPan: Float) {
        val clamped = newPan.coerceIn(-1.0f, 1.0f)
        if (pan != clamped) {
            pan = clamped
            updateGains()
        }
    }

    private fun updateGains() {
        val (l, r) = if (isMonoInput) {
            StereoPanHelper.calculateGains(pan)
        } else {
            StereoPanHelper.calculateStereoBalanceGains(pan)
        }
        leftGain = l
        rightGain = r
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        if (inputAudioFormat.sampleRate <= 0 || inputAudioFormat.channelCount <= 0) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        isMonoInput = inputAudioFormat.channelCount == 1
        updateGains()
        // Always outputs stereo (2 channels)
        return AudioFormat(inputAudioFormat.sampleRate, 2, inputAudioFormat.encoding)
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remainingBytes = inputBuffer.remaining()
        if (remainingBytes == 0) return

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                if (isMonoInput) {
                    val inputSamples = remainingBytes / 2
                    val outputBuffer = replaceOutputBuffer(inputSamples * 4) // 2 channels * 2 bytes
                    while (inputBuffer.hasRemaining()) {
                        val sample = inputBuffer.short.toFloat()
                        val l = (sample * leftGain).roundToInt().coerceIn(-32768, 32767).toShort()
                        val r = (sample * rightGain).roundToInt().coerceIn(-32768, 32767).toShort()
                        outputBuffer.putShort(l)
                        outputBuffer.putShort(r)
                    }
                    outputBuffer.flip()
                } else {
                    val outputBuffer = replaceOutputBuffer(remainingBytes)
                    while (inputBuffer.remaining() >= 4) {
                        val lIn = inputBuffer.short.toFloat()
                        val rIn = inputBuffer.short.toFloat()
                        val l = (lIn * leftGain).roundToInt().coerceIn(-32768, 32767).toShort()
                        val r = (rIn * rightGain).roundToInt().coerceIn(-32768, 32767).toShort()
                        outputBuffer.putShort(l)
                        outputBuffer.putShort(r)
                    }
                    outputBuffer.flip()
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                if (isMonoInput) {
                    val inputSamples = remainingBytes / 4
                    val outputBuffer = replaceOutputBuffer(inputSamples * 8) // 2 channels * 4 bytes
                    while (inputBuffer.hasRemaining()) {
                        val sample = inputBuffer.float
                        outputBuffer.putFloat(sample * leftGain)
                        outputBuffer.putFloat(sample * rightGain)
                    }
                    outputBuffer.flip()
                } else {
                    val outputBuffer = replaceOutputBuffer(remainingBytes)
                    while (inputBuffer.remaining() >= 8) {
                        val lIn = inputBuffer.float
                        val rIn = inputBuffer.float
                        outputBuffer.putFloat(lIn * leftGain)
                        outputBuffer.putFloat(rIn * rightGain)
                    }
                    outputBuffer.flip()
                }
            }
        }
    }
}
