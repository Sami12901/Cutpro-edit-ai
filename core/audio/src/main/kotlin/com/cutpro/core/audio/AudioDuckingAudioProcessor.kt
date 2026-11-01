package com.cutpro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class AudioDuckingAudioProcessor(
    private var duckingSchedule: TimelineDuckingSchedule? = null,
    val duckingEngine: AudioDuckingEngine = AudioDuckingEngine()
) : BaseAudioProcessor() {

    @Volatile
    var isSpeechActive: Boolean = false

    private var anchorPtsUs: Long = 0L
    private var totalFramesProcessedSinceAnchor: Long = 0L

    fun setSchedule(schedule: TimelineDuckingSchedule?) {
        this.duckingSchedule = schedule
    }

    fun seekTo(ptsUs: Long) {
        anchorPtsUs = ptsUs
        totalFramesProcessedSinceAnchor = 0L
        duckingEngine.reset()
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
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remainingBytes = inputBuffer.remaining()
        val bytesPerFrame = inputAudioFormat.bytesPerFrame
        val frameCount = remainingBytes / bytesPerFrame
        if (frameCount == 0) return

        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)
        val sampleRate = inputAudioFormat.sampleRate
        val channels = inputAudioFormat.channelCount

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> {
                for (frameIdx in 0 until frameCount) {
                    val framePtsUs = anchorPtsUs +
                        ((totalFramesProcessedSinceAnchor + frameIdx) * 1_000_000L) / sampleRate

                    val gain = duckingSchedule?.evaluateGainAt(framePtsUs)
                        ?: duckingEngine.process(framePtsUs, isSpeechActive)

                    for (ch in 0 until channels) {
                        val sample = inputBuffer.short.toFloat()
                        val attenuated = (sample * gain).roundToInt().coerceIn(-32768, 32767).toShort()
                        outputBuffer.putShort(attenuated)
                    }
                }
            }
            C.ENCODING_PCM_FLOAT -> {
                for (frameIdx in 0 until frameCount) {
                    val framePtsUs = anchorPtsUs +
                        ((totalFramesProcessedSinceAnchor + frameIdx) * 1_000_000L) / sampleRate

                    val gain = duckingSchedule?.evaluateGainAt(framePtsUs)
                        ?: duckingEngine.process(framePtsUs, isSpeechActive)

                    for (ch in 0 until channels) {
                        val sample = inputBuffer.float
                        outputBuffer.putFloat(sample * gain)
                    }
                }
            }
        }

        totalFramesProcessedSinceAnchor += frameCount
        outputBuffer.flip()
    }

    override fun onFlush() {
        totalFramesProcessedSinceAnchor = 0L
    }

    override fun onReset() {
        anchorPtsUs = 0L
        totalFramesProcessedSinceAnchor = 0L
        duckingEngine.reset()
    }
}
