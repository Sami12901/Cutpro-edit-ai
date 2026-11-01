package com.cutpro.core.export

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cutpro.core.export.model.ExportPlan
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class Media3VideoExportEngineTest {

    private lateinit var context: Context
    private lateinit var engine: Media3VideoExportEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        engine = Media3VideoExportEngine(context)
    }

    private fun createValidProject(tempSourceFile: File): ProjectManifest {
        val media = MediaSourceInfo(
            uriString = tempSourceFile.toURI().toString(),
            fileName = tempSourceFile.name,
            mimeType = "video/mp4",
            durationUs = 10_000_000L,
            width = 1920,
            height = 1080
        )
        val clip = TimelineClip(
            id = "c1",
            trackId = "t1",
            mediaSource = media,
            timelineStartUs = 0L,
            timelineDurationUs = 10_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L
        )
        val track = TimelineTrack(
            id = "t1",
            name = "Video",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )
        return ProjectManifest(
            id = "p1",
            title = "Test",
            durationUs = 10_000_000L,
            tracks = listOf(track)
        )
    }

    @Test
    fun validateExportPlan_successOnValidPlan() {
        val tempSource = File(context.cacheDir, "sample_test_input.mp4").apply {
            writeBytes(ByteArray(1024))
        }
        val project = createValidProject(tempSource)
        val outputFile = File(context.cacheDir, "test_out.mp4")

        val plan = ExportPlan(
            project = project,
            outputFile = outputFile
        )

        val result = engine.validateExportPlan(plan)
        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun validateExportPlan_failsOnEmptyTimeline() {
        val emptyProject = ProjectManifest(
            id = "empty",
            title = "Empty",
            durationUs = 0L,
            tracks = emptyList()
        )
        val plan = ExportPlan(
            project = emptyProject,
            outputFile = File(context.cacheDir, "out.mp4")
        )

        val result = engine.validateExportPlan(plan)
        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun calculateEtaMs_smoothPredictions() {
        // Progress <= 1 or elapsed < 1000ms returns null
        assertThat(Media3VideoExportEngine.calculateEtaMs(1, 500L)).isNull()
        assertThat(Media3VideoExportEngine.calculateEtaMs(0, 2000L)).isNull()
        assertThat(Media3VideoExportEngine.calculateEtaMs(50, 500L)).isNull()

        // 50% at 10s (10000ms) -> total 20s -> remaining 10s (10000ms)
        assertThat(Media3VideoExportEngine.calculateEtaMs(50, 10000L)).isEqualTo(10000L)

        // 25% at 5s (5000ms) -> total 20s -> remaining 15s (15000ms)
        assertThat(Media3VideoExportEngine.calculateEtaMs(25, 5000L)).isEqualTo(15000L)

        // 100% returns 0
        assertThat(Media3VideoExportEngine.calculateEtaMs(100, 12000L)).isEqualTo(0L)
    }

    @Test
    fun cancelExport_idempotentOnNonExistentExport() {
        engine.cancelExport("non_existent_id")
    }
}
