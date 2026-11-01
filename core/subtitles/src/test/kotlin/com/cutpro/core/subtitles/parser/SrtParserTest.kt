package com.cutpro.core.subtitles.parser

import com.cutpro.core.subtitles.model.CaptionCue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SrtParserTest {

    private val sampleSrt = """
        1
        00:00:01,000 --> 00:00:04,000
        Welcome to CutPro Video Editor

        2
        00:00:05,500 --> 00:00:08,250
        Offline native Android editing
    """.trimIndent()

    @Test
    fun parse_standardSrt() {
        val cues = SrtParser.parse(sampleSrt)
        assertThat(cues).hasSize(2)
        assertThat(cues[0].startTimeUs).isEqualTo(1_000_000L)
        assertThat(cues[0].endTimeUs).isEqualTo(4_000_000L)
        assertThat(cues[0].text).isEqualTo("Welcome to CutPro Video Editor")
        assertThat(cues[1].startTimeUs).isEqualTo(5_500_000L)
        assertThat(cues[1].endTimeUs).isEqualTo(8_250_000L)
    }

    @Test
    fun parse_withDotInsteadOfCommaDelimiter() {
        val dotSrt = """
            1
            00:00:01.500 --> 00:00:03.500
            Dot Delimiter Test
        """.trimIndent()

        val cues = SrtParser.parse(dotSrt)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].startTimeUs).isEqualTo(1_500_000L)
        assertThat(cues[0].endTimeUs).isEqualTo(3_500_000L)
        assertThat(cues[0].text).isEqualTo("Dot Delimiter Test")
    }

    @Test
    fun parse_missingSequenceNumbers() {
        val noSeqSrt = """
            00:00:01,000 --> 00:00:03,000
            No Sequence First

            00:00:04,000 --> 00:00:06,000
            No Sequence Second
        """.trimIndent()

        val cues = SrtParser.parse(noSeqSrt)
        assertThat(cues).hasSize(2)
        assertThat(cues[0].text).isEqualTo("No Sequence First")
        assertThat(cues[1].text).isEqualTo("No Sequence Second")
    }

    @Test
    fun parse_stripsHtmlTags() {
        val htmlSrt = """
            1
            00:00:01,000 --> 00:00:02,000
            <b>Bold</b> and <i>Italic</i> with <font color="#ff0000">Color</font>
        """.trimIndent()

        val cues = SrtParser.parse(htmlSrt)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("Bold and Italic with Color")
    }

    @Test
    fun parse_handlesBomMarker() {
        val bomSrt = "\uFEFF1\n00:00:01,000 --> 00:00:02,000\nBOM Cleaned"
        val cues = SrtParser.parse(bomSrt)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("BOM Cleaned")
    }

    @Test
    fun parse_skipsMalformedOrEmptyCues() {
        val malformed = """
            1
            INVALID TIME --> 00:00:04,000
            Bad Timecode

            2
            00:00:05,000 --> 00:00:06,000
               

            3
            00:00:07,000 --> 00:00:08,000
            Valid Cue
        """.trimIndent()

        val cues = SrtParser.parse(malformed)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("Valid Cue")
    }

    @Test
    fun export_roundtripFidelity() {
        val originalCues = SrtParser.parse(sampleSrt)
        val exported = SrtParser.export(originalCues)

        val reParsed = SrtParser.parse(exported)
        assertThat(reParsed).hasSize(originalCues.size)
        for (i in originalCues.indices) {
            assertThat(reParsed[i].startTimeUs).isEqualTo(originalCues[i].startTimeUs)
            assertThat(reParsed[i].endTimeUs).isEqualTo(originalCues[i].endTimeUs)
            assertThat(reParsed[i].text).isEqualTo(originalCues[i].text)
        }
    }
}
