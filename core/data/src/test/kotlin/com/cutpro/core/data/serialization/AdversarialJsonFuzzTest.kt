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
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test

class AdversarialJsonFuzzTest {

    private fun createValidSampleProject(): ProjectManifest {
        val media = MediaSourceInfo(
            uriString = "content://media/external/video/1",
            fileName = "clip.mp4",
            mimeType = "video/mp4",
            durationUs = 10_000_000L,
            width = 1920,
            height = 1080,
            rotationDegrees = 0,
            frameRate = 30.0f,
            audioChannels = 2,
            audioSampleRate = 48000,
            fileSizeBytes = 15_000_000L
        )
        val clip = TimelineClip(
            id = "c-1",
            trackId = "t-1",
            mediaSource = media,
            timelineStartUs = 0L,
            timelineDurationUs = 5_000_000L,
            sourceInUs = 0L,
            sourceOutUs = 5_000_000L,
            speedMultiplier = 1.0f
        )
        val track = TimelineTrack(
            id = "t-1",
            name = "Track 1",
            type = TrackType.VIDEO,
            zIndex = 0,
            clips = listOf(clip)
        )
        return ProjectManifest(
            id = "p-1",
            title = "Test Project",
            createdAt = 1000L,
            modifiedAt = 2000L,
            aspectRatio = CanvasAspectRatio.RATIO_16_9,
            defaultResolution = ExportResolution.RES_1080P,
            defaultFrameRate = ExportFrameRate.FPS_30,
            durationUs = 5_000_000L,
            tracks = listOf(track)
        )
    }

    @Test
    fun testMalformedJsonRejection() {
        val malformedPayloads = listOf(
            "",
            "   ",
            "NOT_JSON",
            "{",
            "}",
            "{,,,,,}",
            "{\"version\": 1, \"metadata\": ",
            "{\"version\": true}",
            "{\"version\": \"1\", \"metadata\": null}",
            "{\"version\": 1, \"tracks\": [{\"clips\": 123}]}",
            "null",
            "[1, 2, 3]",
            "{\"version\": 1, \"canvas\": {\"durationUs\": \"NaN\"}}"
        )

        for (payload in malformedPayloads) {
            try {
                ProjectJsonSerializer.deserialize(payload)
                fail("Expected failure for malformed JSON payload: '$payload'")
            } catch (e: SerializationException) {
                // Expected
            } catch (e: IllegalArgumentException) {
                // Expected
            } catch (e: Exception) {
                // Assert no uncaught runtime crash like NPE or IndexOutOfBounds
                assertThat(e).isInstanceOf(Exception::class.java)
            }
        }
    }

    @Test
    fun testTruncatedJsonRejection() {
        val validJson = ProjectJsonSerializer.serialize(createValidSampleProject())
        val cutIndices = listOf(1, 5, 10, 25, 50, 100, validJson.length / 4, validJson.length / 2, validJson.length - 2, validJson.length - 1)

        for (cutIndex in cutIndices) {
            val truncated = validJson.substring(0, cutIndex)
            try {
                ProjectJsonSerializer.deserialize(truncated)
                fail("Truncated JSON at index $cutIndex should have failed deserialization!")
            } catch (e: SerializationException) {
                // Expected
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun testInjectionStringsIntegrity() {
        val injectionStrings = listOf(
            "'; DROP TABLE projects; --",
            "\" OR \"\"=\"",
            "<script>alert('XSS')</script>",
            "<h1>Injection</h1>",
            "\u0000null_byte_title",
            "../../../../escaped/path",
            "سلام دنیا / مرحبا / বাংলা",
            "Special: & < > \" ' \t \r \n \\ /"
        )

        for (injection in injectionStrings) {
            val project = createValidSampleProject().copy(
                title = injection
            )
            val json = ProjectJsonSerializer.serialize(project)
            val deserialized = ProjectJsonSerializer.deserialize(json)

            assertThat(deserialized.title).isEqualTo(injection)
        }
    }

    @Test
    fun testNegativeNumbersRejectionInDomainMapping() {
        // Timeline start us < 0
        val negativeStartJson = """
        {
            "version": 1,
            "metadata": {"id": "p-neg", "title": "Neg", "createdAt": 1000, "modifiedAt": 2000},
            "canvas": {"aspectRatio": "RATIO_16_9", "defaultResolution": "RES_1080P", "defaultFrameRate": "FPS_30", "durationUs": 5000000},
            "tracks": [{
                "id": "t-1", "name": "V1", "trackType": "VIDEO", "zIndex": 0,
                "clips": [{
                    "id": "c-1", "trackId": "t-1",
                    "mediaSource": {
                        "uriString": "content://1", "fileName": "c.mp4", "mimeType": "video/mp4", "durationUs": 10000000
                    },
                    "timelineStartUs": -1000000,
                    "timelineDurationUs": 5000000,
                    "sourceInUs": 0,
                    "sourceOutUs": 5000000,
                    "speedMultiplier": 1.0
                }]
            }]
        }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            ProjectJsonSerializer.deserialize(negativeStartJson)
        }

        // Speed multiplier <= 0
        val negativeSpeedJson = """
        {
            "version": 1,
            "metadata": {"id": "p-neg", "title": "Neg", "createdAt": 1000, "modifiedAt": 2000},
            "canvas": {"aspectRatio": "RATIO_16_9", "defaultResolution": "RES_1080P", "defaultFrameRate": "FPS_30", "durationUs": 5000000},
            "tracks": [{
                "id": "t-1", "name": "V1", "trackType": "VIDEO", "zIndex": 0,
                "clips": [{
                    "id": "c-1", "trackId": "t-1",
                    "mediaSource": {
                        "uriString": "content://1", "fileName": "c.mp4", "mimeType": "video/mp4", "durationUs": 10000000
                    },
                    "timelineStartUs": 0,
                    "timelineDurationUs": 5000000,
                    "sourceInUs": 0,
                    "sourceOutUs": 5000000,
                    "speedMultiplier": -1.0
                }]
            }]
        }
        """.trimIndent()

        assertThrows(IllegalArgumentException::class.java) {
            ProjectJsonSerializer.deserialize(negativeSpeedJson)
        }
    }

    @Test
    fun testUnknownAndInvalidSchemaVersions() {
        val higherVersionJson = """
        {
            "version": 999,
            "metadata": {"id": "p-future", "title": "Future", "createdAt": 1000, "modifiedAt": 2000},
            "canvas": {"aspectRatio": "RATIO_16_9", "defaultResolution": "RES_1080P", "defaultFrameRate": "FPS_30", "durationUs": 1000},
            "tracks": []
        }
        """.trimIndent()

        assertThrows(UnsupportedSchemaVersionException::class.java) {
            ProjectJsonSerializer.deserialize(higherVersionJson)
        }
    }
}
