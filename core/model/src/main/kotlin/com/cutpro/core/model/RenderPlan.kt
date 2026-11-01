package com.cutpro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class RenderPlan(
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val frameRate: ExportFrameRate = ExportFrameRate.FPS_30,
    val aspectRatio: CanvasAspectRatio = CanvasAspectRatio.RATIO_16_9,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val videoBitrateBps: Int = 12_000_000, // 12 Mbps
    val audioBitrateBps: Int = 192_000,    // 192 kbps
    val durationUs: Long
) {
    init {
        require(videoBitrateBps > 0) { "videoBitrateBps must be > 0" }
        require(audioBitrateBps > 0) { "audioBitrateBps must be > 0" }
        require(durationUs >= 0L) { "durationUs cannot be negative" }
    }

    fun calculateEstimatedOutputSizeBytes(safetyBufferMb: Int = 50, overheadFactor: Double = 1.15): Long {
        val durationSec = durationUs / 1_000_000.0
        val totalBitsPerSec = videoBitrateBps + audioBitrateBps
        val rawBytes = (totalBitsPerSec / 8.0) * durationSec
        val withOverhead = (rawBytes * overheadFactor).toLong()
        val safetyBufferBytes = safetyBufferMb.toLong() * 1024L * 1024L
        return withOverhead + safetyBufferBytes
    }
}
