package com.cutpro.core.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.roundToInt

class VolumeAndFadeAudioProcessor(
    private val clipDurationUs: Long,
    initialVolume: Float = 1.0f,
    initialMuted: Boolean = false,
    fadeInDurationUs: Long = 0L,
    fadeInCurve: FadeCurveType = FadeCurveType.LINEAR,
    fadeOutDurationUs: Long = 0L,
    fadeOutCurve: FadeCurveType = FadeCurveType.LINEAR,
    val softClipMode: SoftClipMode = SoftClipMode.THRESHOLDED_TANH,
    val softClipThreshold: Float = 0.75f
) : BaseAudioProcessor() {

    @Volatile
    var volume: Float = initialVolume.coerceIn(0.0f, 2.0f)
        private set

    @Volatile
    var isMuted: Boolean = initialMuted
        private set

    private var fadeInUs: Long = fadeInDurationUs.coerceAtLeast(0L)
    private var fadeInCurveType: FadeCurveType = fadeInCurve
    private var fadeOutUs: Long = fadeOutDurationUs.coerceAtLeast(0L)
    private var fadeOutCurveType: FadeCurveType = fadeOutCurve

    private var effectiveFadeInUs: Long = 0L
    private var effectiveFadeOutUs: Long = 0L

    // 64-bit zero-drift sample-accurate presentation timestamp tracking
    private var anchorPtsUs: Long = 0L
    private var totalFramesProcessedSinceAnchor: Long = 0L

    init {
        recalculateEffectiveFades()
    }

    fun setVolume(newVolume: Float) {
        volume = newVolume.coerceIn(0.0f, 2.0f)
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
    }

    fun setFades(
        newFadeInUs: Long,
        newFadeInCurve: FadeCurveType,
        newFadeOutUs: Long,
        newFadeOutCurve: FadeCurveType
    ) {
        fadeInUs = newFadeInUs.coerceAtLeast(0L)
        fadeInCurveType = newFadeInCurve
        fadeOutUs = newFadeOutUs.coerceAtLeast(0L)
        fadeOutCurveType = newFadeOutCurve
        recalculateEffectiveFades()
    }

    fun seekTo(ptsUs: Long) {
        anchorPtsUs = ptsUs
        totalFramesProcessedSinceAnchor = 0L
    }

    private fun recalculateEffectiveFades() {
        val totalFade = fadeInUs + fadeOutUs
        if (totalFade == 0L || totalFade <= clipDurationUs) {
            effectiveFadeInUs = fadeInUs
            effectiveFadeOutUs = fadeOutUs
        } else {
            val scale = clipDurationUs.toDouble() / totalFade.toDouble()
            effectiveFadeInUs = (fadeInUs * scale).toLong()
            effectiveFadeOutUs = (fadeOutUs * scale).toLong()
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
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

        if (isMuted || volume == 0.0f) {
            val zeroBytes = frameCount * bytesPerFrame
            for (i in 0 until zeroBytes) {
                outputBuffer.put(0.toByte())
            }
            inputBuffer.position(inputBuffer.position() + zeroBytes)
            totalFramesProcessedSinceAnchor += frameCount
            outputBuffer.flip()
            return
        }

        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> processPcm16(inputBuffer, outputBuffer, frameCount)
            C.ENCODING_PCM_FLOAT -> processPcmFloat(inputBuffer, outputBuffer, frameCount)
        }

        totalFramesProcessedSinceAnchor += frameCount
        outputBuffer.flip()
    }

    private fun processPcm16(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, frameCount: Int) {
        val sampleRate = inputAudioFormat.sampleRate
        val channels = inputAudioFormat.channelCount
        val currentVolume = volume

        for (frameIdx in 0 until frameCount) {
            val framePtsUs = anchorPtsUs +
                ((totalFramesProcessedSinceAnchor + frameIdx) * 1_000_000L) / sampleRate
            val gain = evaluateGainAt(framePtsUs, currentVolume)

            for (ch in 0 until channels) {
                val rawSample = inputBuffer.short
                val normalized = if (rawSample >= 0) {
                    rawSample.toFloat() / 32767.0f
                } else {
                    rawSample.toFloat() / 32768.0f
                }

                val amplified = normalized * gain
                val saturated = if (currentVolume > 1.0f || gain > 1.0f) {
                    SoftClippingLimiter.softClip(amplified, softClipMode, softClipThreshold)
                } else {
                    amplified.coerceIn(-1.0f, 1.0f)
                }

                val outputShort = if (saturated >= 0.0f) {
                    (saturated * 32767.0f).roundToInt().coerceIn(-32768, 32767).toShort()
                } else {
                    (saturated * 32768.0f).roundToInt().coerceIn(-32768, 32767).toShort()
                }
                outputBuffer.putShort(outputShort)
            }
        }
    }

    private fun processPcmFloat(inputBuffer: ByteBuffer, outputBuffer: ByteBuffer, frameCount: Int) {
        val sampleRate = inputAudioFormat.sampleRate
        val channels = inputAudioFormat.channelCount
        val currentVolume = volume

        for (frameIdx in 0 until frameCount) {
            val framePtsUs = anchorPtsUs +
                ((totalFramesProcessedSinceAnchor + frameIdx) * 1_000_000L) / sampleRate
            val gain = evaluateGainAt(framePtsUs, currentVolume)

            for (ch in 0 until channels) {
                val sample = inputBuffer.float
                val amplified = sample * gain
                val saturated = if (currentVolume > 1.0f || gain > 1.0f) {
                    SoftClippingLimiter.softClip(amplified, softClipMode, softClipThreshold)
                } else {
                    amplified.coerceIn(-1.0f, 1.0f)
                }
                outputBuffer.putFloat(saturated)
            }
        }
    }

    private fun evaluateGainAt(ptsUs: Long, baseVolume: Float): Float {
        if (ptsUs < 0L || (clipDurationUs > 0L && ptsUs >= clipDurationUs)) return 0.0f

        // Region 1: Fade-in
        if (effectiveFadeInUs > 0L && ptsUs < effectiveFadeInUs) {
            val progress = ptsUs.toFloat() / effectiveFadeInUs.toFloat()
            return baseVolume * FadeCurveEvaluator.evaluateFadeIn(progress, fadeInCurveType)
        }

        // Region 3: Fade-out
        val fadeOutStartUs = clipDurationUs - effectiveFadeOutUs
        if (effectiveFadeOutUs > 0L && ptsUs >= fadeOutStartUs) {
            val progress = (ptsUs - fadeOutStartUs).toFloat() / effectiveFadeOutUs.toFloat()
            return baseVolume * FadeCurveEvaluator.evaluateFadeOut(progress, fadeOutCurveType)
        }

        // Region 2: Plateau
        return baseVolume
    }

    override fun onFlush() {
        totalFramesProcessedSinceAnchor = 0L
    }

    override fun onReset() {
        anchorPtsUs = 0L
        totalFramesProcessedSinceAnchor = 0L
    }
}
