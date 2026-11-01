package com.cutpro.core.subtitles.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WebVttParserTest {

    private val sampleVtt = """
        WEBVTT

        00:00:01.000 --> 00:00:04.000
        Welcome to CutPro Video Editor

        00:00:05.500 --> 00:00:08.250
        Offline native Android editing
    """.trimIndent()

    @Test
    fun parse_standardVtt() {
        val cues = WebVttParser.parse(sampleVtt)
        assertThat(cues).hasSize(2)
        assertThat(cues[0].startTimeUs).isEqualTo(1_000_000L)
        assertThat(cues[0].endTimeUs).isEqualTo(4_000_000L)
        assertThat(cues[0].text).isEqualTo("Welcome to CutPro Video Editor")
        assertThat(cues[1].startTimeUs).isEqualTo(5_500_000L)
        assertThat(cues[1].endTimeUs).isEqualTo(8_250_000L)
    }

    @Test
    fun parse_missingHoursHandled() {
        val shortVtt = """
            WEBVTT

            01:23.456 --> 01:25.789
            Short timecode without hours
        """.trimIndent()

        val cues = WebVttParser.parse(shortVtt)
        assertThat(cues).hasSize(1)
        val expectedStart = (1 * 60 + 23) * 1_000_000L + 456_000L
        val expectedEnd = (1 * 60 + 25) * 1_000_000L + 789_000L
        assertThat(cues[0].startTimeUs).isEqualTo(expectedStart)
        assertThat(cues[0].endTimeUs).isEqualTo(expectedEnd)
    }

    @Test
    fun parse_cueSettingsIgnored() {
        val settingsVtt = """
            WEBVTT

            00:00:01.000 --> 00:00:03.000 position:10%,line:90% align:center
            Styled Cue Settings Ignored
        """.trimIndent()

        val cues = WebVttParser.parse(settingsVtt)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("Styled Cue Settings Ignored")
    }

    @Test
    fun parse_inlineTagsStripped() {
        val taggedVtt = """
            WEBVTT

            00:00:01.000 --> 00:00:03.000
            <c.yellow>Colored</c> and <v Speaker>Voice</v>
        """.trimIndent()

        val cues = WebVttParser.parse(taggedVtt)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("Colored and Voice")
    }

    @Test
    fun parse_missingWebvttHeaderResilient() {
        val noHeaderVtt = """
            00:01.000 --> 00:03.000
            Missing Header Cue
        """.trimIndent()

        val cues = WebVttParser.parse(noHeaderVtt)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("Missing Header Cue")
    }

    @Test
    fun export_roundtripFidelity() {
        val cues = WebVttParser.parse(sampleVtt)
        val exported = WebVttParser.export(cues)
        assertThat(exported).startsWith("WEBVTT")

        val reparsed = WebVttParser.parse(exported)
        assertThat(reparsed).hasSize(cues.size)
        assertThat(reparsed[0].text).isEqualTo(cues[0].text)
        assertThat(reparsed[0].startTimeUs).isEqualTo(cues[0].startTimeUs)
        assertThat(reparsed[0].endTimeUs).isEqualTo(cues[0].endTimeUs)
    }
}
