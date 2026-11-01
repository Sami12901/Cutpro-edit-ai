package com.cutpro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExportFrameRate(val fps: Int, val displayName: String) {
    FPS_24(24, "24 fps (Cinematic)"),
    FPS_30(30, "30 fps (Standard)"),
    FPS_60(60, "60 fps (Smooth)");

    val frameDurationUs: Long get() = 1_000_000L / fps

    fun timestampToFrameIndex(timestampUs: Long): Long {
        require(timestampUs >= 0L) { "timestampUs ($timestampUs) cannot be negative" }
        return (timestampUs * fps) / 1_000_000L
    }

    fun frameIndexToTimestamp(frameIndex: Long): Long {
        require(frameIndex >= 0L) { "frameIndex ($frameIndex) cannot be negative" }
        return (frameIndex * 1_000_000L) / fps
    }
}
