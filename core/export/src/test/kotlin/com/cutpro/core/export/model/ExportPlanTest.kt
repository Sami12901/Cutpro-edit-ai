package com.cutpro.core.export.model

import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.model.VideoCodec
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class ExportPlanTest {

    private fun createSampleProject(durationUs: Long = 10_000_000L): ProjectManifest {
        val media = MediaSourceInfo(
            uriString = "file:///tmp/sample.mp4",
            fileName = "sample.mp4",
            mimeType = "video/mp4",
            durationUs = durationUs,
            width = 1920,
            height = 1080
        )
        val clip = TimelineClip(
            id = "c1",
            trackId = "t1",
            mediaSource = media,
            timelineStartUs = 0L,
            timelineDurationUs = durationUs,
            sourceInUs = 0L,
            sourceOutUs = durationUs
        )
        val track = TimelineTrack(
            id = "t1",
            name = "Video 1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )
        return ProjectManifest(
            id = "p1",
            title = "Test Project",
            durationUs = durationUs,
            tracks = listOf(track)
        )
    }

    @Test
    fun calculateOutputDimensions_1080p_macroblockAligned() {
        val plan16_9 = ExportPlan(
            project = createSampleProject(),
            resolution = ExportResolution.RES_1080P,
            targetAspectRatio = CanvasAspectRatio.RATIO_16_9,
            outputFile = File("/tmp/out.mp4")
        )
        val (w16_9, h16_9) = plan16_9.calculateOutputDimensions()
        assertThat(w16_9 % 16).isEqualTo(0)
        assertThat(h16_9 % 16).isEqualTo(0)
        assertThat(w16_9).isEqualTo(1936)
        assertThat(h16_9).isEqualTo(1088)


        val plan9_16 = ExportPlan(
            project = createSampleProject(),
            resolution = ExportResolution.RES_1080P,
            targetAspectRatio = CanvasAspectRatio.RATIO_9_16,
            outputFile = File("/tmp/out.mp4")
        )
        val (w9_16, h9_16) = plan9_16.calculateOutputDimensions()
        assertThat(w9_16 % 16).isEqualTo(0)
        assertThat(h9_16 % 16).isEqualTo(0)

        val plan1_1 = ExportPlan(
            project = createSampleProject(),
            resolution = ExportResolution.RES_1080P,
            targetAspectRatio = CanvasAspectRatio.RATIO_1_1,
            outputFile = File("/tmp/out.mp4")
        )
        val (w1_1, h1_1) = plan1_1.calculateOutputDimensions()
        assertThat(w1_1 % 16).isEqualTo(0)
        assertThat(h1_1 % 16).isEqualTo(0)
    }

    @Test
    fun calculateEstimatedOutputSizeBytes_includesSafetyBufferAndOverhead() {
        val project = createSampleProject(durationUs = 10_000_000L) // 10 seconds
        val plan = ExportPlan(
            project = project,
            videoBitrateBps = 12_000_000,
            audioBitrateBps = 192_000,
            outputFile = File("/tmp/out.mp4")
        )

        val estimatedBytes = plan.calculateEstimatedOutputSizeBytes(safetyBufferMb = 50, overheadFactor = 1.15)
        val rawBytes = ((12_000_000 + 192_000) / 8.0) * 10.0
        val expectedWithOverhead = (rawBytes * 1.15).toLong()
        val expectedTotal = expectedWithOverhead + (50L * 1024L * 1024L)

        assertThat(estimatedBytes).isEqualTo(expectedTotal)
        assertThat(estimatedBytes).isGreaterThan(50L * 1024L * 1024L)
    }

    @Test
    fun validationRejectsInvalidParameters() {
        val project = createSampleProject()
        assertThrows(IllegalArgumentException::class.java) {
            ExportPlan(
                exportId = "",
                project = project,
                outputFile = File("/tmp/out.mp4")
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            ExportPlan(
                project = project,
                videoBitrateBps = 0,
                outputFile = File("/tmp/out.mp4")
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            ExportPlan(
                project = project,
                audioBitrateBps = -1,
                outputFile = File("/tmp/out.mp4")
            )
        }
    }
}
