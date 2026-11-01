package com.cutpro.core.data.serialization

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
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectJsonSerializationTest {

    private val sampleMedia = MediaSourceInfo(
        uriString = "content://media/external/video/1",
        fileName = "sunset.mp4",
        mimeType = "video/mp4",
        durationUs = 15_000_000L,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        frameRate = 30.0f,
        audioChannels = 2,
        audioSampleRate = 48000,
        fileSizeBytes = 25_000_000L
    )

    private val sampleClip = TimelineClip(
        id = "clip-1",
        trackId = "track-1",
        mediaSource = sampleMedia,
        timelineStartUs = 2_000_000L,
        timelineDurationUs = 8_000_000L,
        sourceInUs = 1_000_000L,
        sourceOutUs = 9_000_000L,
        speedMultiplier = 1.0f,
        fitMode = ClipFitMode.FILL,
        transform = ClipTransform(scaleX = 1.2f, scaleY = 1.2f, rotationDegrees = 45f),
        audioProperties = ClipAudioProperties(volume = 1.5f, fadeInDurationUs = 500_000L)
    )

    private val sampleTrack = TimelineTrack(
        id = "track-1",
        name = "Main Video",
        type = TrackType.VIDEO,
        zIndex = 0,
        clips = listOf(sampleClip)
    )

    private val sampleProject = ProjectManifest(
        id = "proj-xyz",
        title = "Vacation Video",
        createdAt = 1700000000000L,
        modifiedAt = 1700000500000L,
        aspectRatio = CanvasAspectRatio.RATIO_9_16,
        defaultResolution = ExportResolution.RES_1080P,
        defaultFrameRate = ExportFrameRate.FPS_60,
        durationUs = 10_000_000L,
        tracks = listOf(sampleTrack)
    )

    @Test
    fun testJsonV1RoundTripFidelity() {
        val serialized = ProjectJsonSerializer.serialize(sampleProject)
        assertThat(serialized).contains("\"version\": 1")
        assertThat(serialized).contains("\"title\": \"Vacation Video\"")
        assertThat(serialized).contains("\"aspectRatio\": \"RATIO_9_16\"")

        val deserialized = ProjectJsonSerializer.deserialize(serialized)

        assertThat(deserialized.id).isEqualTo(sampleProject.id)
        assertThat(deserialized.title).isEqualTo(sampleProject.title)
        assertThat(deserialized.aspectRatio).isEqualTo(sampleProject.aspectRatio)
        assertThat(deserialized.defaultResolution).isEqualTo(sampleProject.defaultResolution)
        assertThat(deserialized.defaultFrameRate).isEqualTo(sampleProject.defaultFrameRate)
        assertThat(deserialized.durationUs).isEqualTo(sampleProject.durationUs)

        assertThat(deserialized.tracks).hasSize(1)
        val deserializedTrack = deserialized.tracks[0]
        assertThat(deserializedTrack.id).isEqualTo(sampleTrack.id)
        assertThat(deserializedTrack.clips).hasSize(1)

        val deserializedClip = deserializedTrack.clips[0]
        assertThat(deserializedClip.id).isEqualTo(sampleClip.id)
        assertThat(deserializedClip.timelineStartUs).isEqualTo(sampleClip.timelineStartUs)
        assertThat(deserializedClip.timelineDurationUs).isEqualTo(sampleClip.timelineDurationUs)
        assertThat(deserializedClip.sourceInUs).isEqualTo(sampleClip.sourceInUs)
        assertThat(deserializedClip.sourceOutUs).isEqualTo(sampleClip.sourceOutUs)
        assertThat(deserializedClip.transform.rotationDegrees).isEqualTo(45f)
        assertThat(deserializedClip.audioProperties.volume).isEqualTo(1.5f)
    }

    @Test
    fun testForwardCompatibilityIgnoresUnknownKeys() {
        // JSON containing unexpected fields from future versions
        val futureJson = """
        {
            "version": 1,
            "futureSuperField": "magic",
            "metadata": {
                "id": "proj-future",
                "title": "Future Project",
                "createdAt": 1000,
                "modifiedAt": 2000,
                "appVersion": "2.0.0",
                "aiEnhancerModel": "gemini-nano"
            },
            "canvas": {
                "aspectRatio": "RATIO_16_9",
                "defaultResolution": "RES_720P",
                "defaultFrameRate": "FPS_30",
                "durationUs": 5000000,
                "colorSpace": "BT2020"
            },
            "tracks": []
        }
        """.trimIndent()

        val parsed = ProjectJsonSerializer.deserialize(futureJson)
        assertThat(parsed.id).isEqualTo("proj-future")
        assertThat(parsed.title).isEqualTo("Future Project")
        assertThat(parsed.aspectRatio).isEqualTo(CanvasAspectRatio.RATIO_16_9)
    }

    @Test
    fun testUnsupportedVersionThrowsException() {
        val version2Json = """
        {
            "version": 2,
            "metadata": {
                "id": "proj-v2",
                "title": "V2 Project",
                "createdAt": 1000,
                "modifiedAt": 2000
            },
            "canvas": {
                "aspectRatio": "RATIO_16_9",
                "defaultResolution": "RES_1080P",
                "defaultFrameRate": "FPS_30",
                "durationUs": 0
            },
            "tracks": []
        }
        """.trimIndent()

        assertThrows(UnsupportedSchemaVersionException::class.java) {
            ProjectJsonSerializer.deserialize(version2Json)
        }
    }
}
