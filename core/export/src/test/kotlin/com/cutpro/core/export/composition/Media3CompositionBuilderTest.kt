package com.cutpro.core.export.composition

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cutpro.core.export.model.ExportPlan
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ClipAudioProperties
import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.model.VideoCodec
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class Media3CompositionBuilderTest {

    private fun createMultitrackProject(): ProjectManifest {
        val videoMedia = MediaSourceInfo(
            uriString = "file:///sdcard/Movies/sample_video.mp4",
            fileName = "sample_video.mp4",
            mimeType = "video/mp4",
            durationUs = 10_000_000L,
            width = 1920,
            height = 1080
        )
        val audioMedia = MediaSourceInfo(
            uriString = "file:///sdcard/Music/bgm.mp3",
            fileName = "bgm.mp3",
            mimeType = "audio/mpeg",
            durationUs = 10_000_000L,
            audioChannels = 2
        )

        // Clip 1: start at 1s (gap 1s before), 4s duration, 2x speed
        val videoClip = TimelineClip(
            id = "vclip1",
            trackId = "track_v1",
            mediaSource = videoMedia,
            timelineStartUs = 1_000_000L,
            timelineDurationUs = 4_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 8_000_000L,
            speedMultiplier = 2.0f,
            fitMode = ClipFitMode.FILL,
            transform = ClipTransform(scaleX = 1.2f, scaleY = 1.2f, rotationDegrees = 15f),
            audioProperties = ClipAudioProperties(volume = 0.8f, pan = -0.5f)
        )

        val audioClip = TimelineClip(
            id = "aclip1",
            trackId = "track_a1",
            mediaSource = audioMedia,
            timelineStartUs = 0L,
            timelineDurationUs = 10_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 10_000_000L,
            audioProperties = ClipAudioProperties(volume = 0.5f, pan = 0.2f)
        )

        val vTrack = TimelineTrack(
            id = "track_v1",
            name = "Main Video",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(videoClip)
        )

        val aTrack = TimelineTrack(
            id = "track_a1",
            name = "Background Audio",
            type = TrackType.AUDIO,
            zIndex = 1,
            clips = listOf(audioClip)
        )

        return ProjectManifest(
            id = "proj_test_multi",
            title = "Multitrack Test",
            durationUs = 10_000_000L,
            tracks = listOf(vTrack, aTrack)
        )
    }

    @Test
    fun buildComposition_producesValidMultitrackComposition() {
        val project = createMultitrackProject()
        val plan = ExportPlan(
            project = project,
            resolution = ExportResolution.RES_1080P,
            frameRate = ExportFrameRate.FPS_30,
            codec = VideoCodec.H264,
            targetAspectRatio = CanvasAspectRatio.RATIO_16_9,
            outputFile = File("/tmp/test_output.mp4")
        )

        val builder = Media3CompositionBuilder()
        val composition = builder.buildComposition(plan)

        assertThat(composition).isNotNull()
        // Primary video sequence + Dedicated audio sequence
        assertThat(composition.sequences).hasSize(2)
    }

    @Test
    fun buildComposition_emptyVideoTrack_producesFallbackSequence() {
        val emptyProject = ProjectManifest(
            id = "empty_proj",
            title = "Empty Proj",
            durationUs = 5_000_000L,
            tracks = emptyList()
        )
        val plan = ExportPlan(
            project = emptyProject,
            outputFile = File("/tmp/out.mp4")
        )

        val builder = Media3CompositionBuilder()
        val composition = builder.buildComposition(plan)

        assertThat(composition).isNotNull()
        assertThat(composition.sequences).hasSize(1)
    }
}
