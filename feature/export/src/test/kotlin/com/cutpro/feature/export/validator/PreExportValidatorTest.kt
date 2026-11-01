package com.cutpro.feature.export.validator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.RenderPlan
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.feature.export.model.ExportValidationResult
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class PreExportValidatorTest {

    private lateinit var context: Context
    private lateinit var validator: PreExportValidator

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        validator = PreExportValidator(context)
    }

    private fun createValidProject(sourceFile: File): ProjectManifest {
        val media = MediaSourceInfo(
            uriString = sourceFile.toURI().toString(),
            fileName = sourceFile.name,
            mimeType = "video/mp4",
            durationUs = 5_000_000L
        )
        val clip = TimelineClip(
            id = "c1",
            trackId = "t1",
            mediaSource = media,
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L
        )
        val track = TimelineTrack(
            id = "t1",
            name = "Track",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )
        return ProjectManifest(
            id = "p1",
            title = "Test",
            durationUs = 5_000_000L,
            tracks = listOf(track)
        )
    }

    @Test
    fun validate_passesOnValidProjectWithAccessibleMedia() {
        val tempFile = File(context.cacheDir, "sample_valid.mp4").apply {
            writeBytes(ByteArray(1024))
        }
        val project = createValidProject(tempFile)
        val plan = RenderPlan(durationUs = 5_000_000L)

        val result = validator.validate(project, plan, context.cacheDir)
        assertThat(result).isEqualTo(ExportValidationResult.Valid)
    }

    @Test
    fun validate_rejectsEmptyTimeline() {
        val project = ProjectManifest(
            id = "p_empty",
            title = "Empty",
            durationUs = 0L,
            tracks = emptyList()
        )
        val plan = RenderPlan(durationUs = 0L)

        val result = validator.validate(project, plan, context.cacheDir)
        assertThat(result).isInstanceOf(ExportValidationResult.EmptyTimeline::class.java)
    }

    @Test
    fun validate_rejectsMissingSourceMedia() {
        val missingFile = File(context.cacheDir, "non_existent_media_123.mp4")
        val project = createValidProject(missingFile)
        val plan = RenderPlan(durationUs = 5_000_000L)

        val result = validator.validate(project, plan, context.cacheDir)
        assertThat(result).isInstanceOf(ExportValidationResult.MissingSourceMedia::class.java)
    }
}
