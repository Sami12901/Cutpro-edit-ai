package com.cutpro.core.export.model

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.RenderPlan
import com.cutpro.core.model.VideoCodec
import java.io.File
import java.util.UUID

data class ExportPlan(
    val exportId: String = UUID.randomUUID().toString(),
    val project: ProjectManifest,
    val resolution: ExportResolution = ExportResolution.RES_1080P,
    val frameRate: ExportFrameRate = ExportFrameRate.FPS_30,
    val codec: VideoCodec = VideoCodec.H264,
    val targetAspectRatio: CanvasAspectRatio = CanvasAspectRatio.RATIO_16_9,
    val outputFile: File,
    val destinationUri: String? = null,
    val videoBitrateBps: Int = 12_000_000,
    val audioBitrateBps: Int = 192_000,
    val includeSubtitles: Boolean = true,
    val includeMotionGraphics: Boolean = true
) {
    init {
        require(exportId.isNotBlank()) { "exportId cannot be blank" }
        require(videoBitrateBps > 0) { "videoBitrateBps must be > 0" }
        require(audioBitrateBps > 0) { "audioBitrateBps must be > 0" }
    }

    fun toRenderPlan(): RenderPlan {
        return RenderPlan(
            resolution = resolution,
            frameRate = frameRate,
            aspectRatio = targetAspectRatio,
            videoCodec = codec,
            videoBitrateBps = videoBitrateBps,
            audioBitrateBps = audioBitrateBps,
            durationUs = project.durationUs
        )
    }

    fun calculateOutputDimensions(): Pair<Int, Int> {
        val targetHeight = resolution.height
        return targetAspectRatio.calculateMacroblockDimensions(targetHeight, macroblockSize = 16)
    }

    fun calculateEstimatedOutputSizeBytes(safetyBufferMb: Int = 50, overheadFactor: Double = 1.15): Long {
        return toRenderPlan().calculateEstimatedOutputSizeBytes(safetyBufferMb, overheadFactor)
    }
}
