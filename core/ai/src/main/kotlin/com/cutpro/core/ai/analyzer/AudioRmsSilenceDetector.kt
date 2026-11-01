package com.cutpro.core.ai.analyzer

import kotlinx.serialization.Serializable

@Serializable
data class SilenceDetectionConfig(
    val silenceThresholdDb: Float = -40.0f,
    val minSilenceDurationUs: Long = 500_000L,
    val chunkDurationUs: Long = 20_000L,
    val hysteresisDb: Float = 3.0f,
    val adaptiveNoiseFloor: Boolean = false
) {
    init {
        require(silenceThresholdDb in -100.0f..0.0f) {
            "silenceThresholdDb ($silenceThresholdDb) must be in [-100.0, 0.0] dBFS"
        }
        require(minSilenceDurationUs > 0L) {
            "minSilenceDurationUs ($minSilenceDurationUs) must be > 0"
        }
        require(chunkDurationUs > 0L) {
            "chunkDurationUs ($chunkDurationUs) must be > 0"
        }
        require(hysteresisDb >= 0.0f) {
            "hysteresisDb ($hysteresisDb) must be >= 0.0 dB"
        }
    }
}

@Serializable
data class SilenceInterval(
    val startUs: Long,
    val endUs: Long,
    val averageDb: Float,
    val peakDb: Float
) {
    val durationUs: Long get() = endUs - startUs

    init {
        require(startUs >= 0L) { "startUs ($startUs) must be >= 0" }
        require(endUs >= startUs) { "endUs ($endUs) must be >= startUs ($startUs)" }
    }
}

/**
 * AI-01: AudioRmsSilenceDetector
 * Analyzes audio energy in 20ms frames using RMS, 3dB hysteresis margin,
 * configurable silence thresholds, and adaptive noise floor estimation.
 */
class AudioRmsSilenceDetector(
    val config: SilenceDetectionConfig = SilenceDetectionConfig()
) {
    fun detectSilencesPcm16(
        samples: ShortArray,
        sampleRate: Int,
        channelCount: Int,
        startPtsUs: Long = 0L
    ): List<SilenceInterval> {
        require(sampleRate > 0) { "sampleRate must be > 0" }
        require(channelCount > 0) { "channelCount must be > 0" }
        if (samples.isEmpty()) return emptyList()

        val framesPerChunk = ((sampleRate * (config.chunkDurationUs / 1000L)) / 1000L).toInt().coerceAtLeast(1)
        val samplesPerChunk = framesPerChunk * channelCount
        val totalChunks = (samples.size + samplesPerChunk - 1) / samplesPerChunk

        val chunkEnergies = FloatArray(totalChunks)
        for (chunkIdx in 0 until totalChunks) {
            val offset = chunkIdx * samplesPerChunk
            val length = minOf(samplesPerChunk, samples.size - offset)
            chunkEnergies[chunkIdx] = AudioRmsCalculator.calculatePcm16ChunkDb(samples, offset, length)
        }

        val effectiveThresholdDb = if (config.adaptiveNoiseFloor) {
            estimateAdaptiveThreshold(chunkEnergies)
        } else {
            config.silenceThresholdDb
        }

        return evaluateSilenceIntervals(chunkEnergies, framesPerChunk, sampleRate, startPtsUs, effectiveThresholdDb)
    }

    fun detectSilencesPcmFloat(
        samples: FloatArray,
        sampleRate: Int,
        channelCount: Int,
        startPtsUs: Long = 0L
    ): List<SilenceInterval> {
        require(sampleRate > 0) { "sampleRate must be > 0" }
        require(channelCount > 0) { "channelCount must be > 0" }
        if (samples.isEmpty()) return emptyList()

        val framesPerChunk = ((sampleRate * (config.chunkDurationUs / 1000L)) / 1000L).toInt().coerceAtLeast(1)
        val samplesPerChunk = framesPerChunk * channelCount
        val totalChunks = (samples.size + samplesPerChunk - 1) / samplesPerChunk

        val chunkEnergies = FloatArray(totalChunks)
        for (chunkIdx in 0 until totalChunks) {
            val offset = chunkIdx * samplesPerChunk
            val length = minOf(samplesPerChunk, samples.size - offset)
            chunkEnergies[chunkIdx] = AudioRmsCalculator.calculatePcmFloatChunkDb(samples, offset, length)
        }

        val effectiveThresholdDb = if (config.adaptiveNoiseFloor) {
            estimateAdaptiveThreshold(chunkEnergies)
        } else {
            config.silenceThresholdDb
        }

        return evaluateSilenceIntervals(chunkEnergies, framesPerChunk, sampleRate, startPtsUs, effectiveThresholdDb)
    }

    private fun evaluateSilenceIntervals(
        chunkEnergies: FloatArray,
        framesPerChunk: Int,
        sampleRate: Int,
        startPtsUs: Long,
        thresholdDb: Float
    ): List<SilenceInterval> {
        val result = mutableListOf<SilenceInterval>()
        var inSilence = false
        var silenceStartChunk = 0
        var sumDb = 0.0
        var peakDb = -100.0f
        var chunkCount = 0

        val exitThresholdDb = thresholdDb + config.hysteresisDb

        for (i in chunkEnergies.indices) {
            val energy = chunkEnergies[i]
            val isChunkSilent = if (inSilence) energy <= exitThresholdDb else energy <= thresholdDb

            if (isChunkSilent) {
                if (!inSilence) {
                    inSilence = true
                    silenceStartChunk = i
                    sumDb = 0.0
                    peakDb = -100.0f
                    chunkCount = 0
                }
                sumDb += energy
                if (energy > peakDb) peakDb = energy
                chunkCount++
            } else {
                if (inSilence) {
                    inSilence = false
                    val intervalStartUs = startPtsUs + (silenceStartChunk.toLong() * framesPerChunk * 1_000_000L) / sampleRate
                    val intervalEndUs = startPtsUs + (i.toLong() * framesPerChunk * 1_000_000L) / sampleRate
                    if (intervalEndUs - intervalStartUs >= config.minSilenceDurationUs) {
                        result.add(
                            SilenceInterval(
                                startUs = intervalStartUs,
                                endUs = intervalEndUs,
                                averageDb = (sumDb / chunkCount).toFloat(),
                                peakDb = peakDb
                            )
                        )
                    }
                }
            }
        }

        if (inSilence) {
            val intervalStartUs = startPtsUs + (silenceStartChunk.toLong() * framesPerChunk * 1_000_000L) / sampleRate
            val intervalEndUs = startPtsUs + (chunkEnergies.size.toLong() * framesPerChunk * 1_000_000L) / sampleRate
            if (intervalEndUs - intervalStartUs >= config.minSilenceDurationUs) {
                result.add(
                    SilenceInterval(
                        startUs = intervalStartUs,
                        endUs = intervalEndUs,
                        averageDb = (sumDb / chunkCount).toFloat(),
                        peakDb = peakDb
                    )
                )
            }
        }

        return result
    }

    private fun estimateAdaptiveThreshold(energies: FloatArray): Float {
        if (energies.isEmpty()) return config.silenceThresholdDb
        val sorted = energies.clone().apply { sort() }
        val percentileIndex = (sorted.size * 0.10).toInt().coerceIn(0, sorted.size - 1)
        val noiseFloorDb = sorted[percentileIndex]
        val computed = noiseFloorDb + 6.0f
        return computed.coerceIn(-50.0f, -30.0f)
    }
}
