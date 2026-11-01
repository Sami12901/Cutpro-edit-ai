package com.cutpro.core.ai.analyzer

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Root Mean Square (RMS) energy calculator for PCM 16-bit and 32-bit float audio frames.
 */
object AudioRmsCalculator {
    const val SILENCE_DB_FLOOR: Float = -100.0f
    private const val EPSILON: Double = 1e-5

    fun calculateRmsPcm16(samples: ShortArray, startIndex: Int = 0, length: Int = samples.size): Float {
        if (length <= 0 || startIndex >= samples.size) return 0.0f
        val actualLength = minOf(length, samples.size - startIndex)
        if (actualLength <= 0) return 0.0f

        var sumSquares = 0.0
        for (i in 0 until actualLength) {
            val sample = samples[startIndex + i]
            val norm = if (sample >= 0) {
                sample.toDouble() / 32767.0
            } else {
                sample.toDouble() / 32768.0
            }
            sumSquares += norm * norm
        }
        return sqrt(sumSquares / actualLength).toFloat()
    }

    fun calculateRmsPcmFloat(samples: FloatArray, startIndex: Int = 0, length: Int = samples.size): Float {
        if (length <= 0 || startIndex >= samples.size) return 0.0f
        val actualLength = minOf(length, samples.size - startIndex)
        if (actualLength <= 0) return 0.0f

        var sumSquares = 0.0
        for (i in 0 until actualLength) {
            val s = samples[startIndex + i].toDouble()
            sumSquares += s * s
        }
        return sqrt(sumSquares / actualLength).toFloat()
    }

    fun rmsToDb(rms: Float): Float {
        if (rms <= EPSILON || rms.isNaN()) return SILENCE_DB_FLOOR
        val db = 20.0 * log10(rms.toDouble())
        return db.toFloat().coerceAtLeast(SILENCE_DB_FLOOR)
    }

    fun calculatePcm16ChunkDb(samples: ShortArray, startIndex: Int = 0, length: Int = samples.size): Float {
        val rms = calculateRmsPcm16(samples, startIndex, length)
        return rmsToDb(rms)
    }

    fun calculatePcmFloatChunkDb(samples: FloatArray, startIndex: Int = 0, length: Int = samples.size): Float {
        val rms = calculateRmsPcmFloat(samples, startIndex, length)
        return rmsToDb(rms)
    }
}
