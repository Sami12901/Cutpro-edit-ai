package com.cutpro.core.subtitles

import android.graphics.Bitmap
import android.graphics.Canvas
import com.cutpro.core.motiongraphics.model.TemplateInstance
import com.cutpro.core.motiongraphics.renderer.MotionGraphicsCanvasRenderer
import com.cutpro.core.motiongraphics.schema.ParameterDefinition
import com.cutpro.core.motiongraphics.schema.ParameterType
import com.cutpro.core.motiongraphics.schema.ZeroEmojiValidator
import com.cutpro.core.motiongraphics.templates.TemplateRegistry
import com.cutpro.core.subtitles.karaoke.KaraokeEngine
import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.CaptionLayout
import com.cutpro.core.subtitles.model.CaptionStyle
import com.cutpro.core.subtitles.model.TimedWord
import com.cutpro.core.subtitles.parser.SrtParser
import com.cutpro.core.subtitles.parser.WebVttParser
import com.cutpro.core.subtitles.renderer.SubtitleCanvasRenderer
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SubtitlesAndTemplatesAdversarialTest {

    // =========================================================================
    // 1. Zero-Emoji Security Stress
    // =========================================================================

    @Test
    fun zeroEmoji_standardSmilies_rejected() {
        val smilies = listOf(
            "\uD83D\uDE00", // Grinning face
            "\uD83D\uDE02", // Tears of joy
            "\uD83D\uDE0D", // Heart eyes
            "\uD83D\uDE18", // Face blowing kiss
            "\uD83D\uDE21"  // Pouting face
        )
        for (emoji in smilies) {
            assertThat(ZeroEmojiValidator.containsEmoji(emoji)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(emoji, "smiley_test")
            }
        }
    }

    @Test
    fun zeroEmoji_surrogatePairs_rejected() {
        val surrogatePairs = listOf(
            "\uD83D\uDE80", // Rocket (Transport and Map)
            "\uD83C\uDF0D", // Earth Globe (Misc Symbols and Pictographs)
            "\uD83E\uDD84", // Unicorn (Supplemental Symbols and Pictographs)
            "\uD83E\uDE70", // Ballet Shoes (Symbols and Pictographs Ext-A)
            "Prefix \uD83D\uDE80 Suffix"
        )
        for (emoji in surrogatePairs) {
            assertThat(ZeroEmojiValidator.containsEmoji(emoji)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(emoji, "surrogate_test")
            }
        }
    }

    @Test
    fun zeroEmoji_skinToneModifiers_rejected() {
        val skinTones = listOf(
            "\uD83C\uDFFB", // Fitzpatrick type 1-2
            "\uD83C\uDFFC", // Fitzpatrick type 3
            "\uD83C\uDFFD", // Fitzpatrick type 4
            "\uD83C\uDFFE", // Fitzpatrick type 5
            "\uD83C\uDFFF", // Fitzpatrick type 6
            "\uD83D\uDC4D\uD83C\uDFFB", // Thumbs up with light skin tone
            "\uD83D\uDC4B\uD83C\uDFFF"  // Waving hand with dark skin tone
        )
        for (emoji in skinTones) {
            assertThat(ZeroEmojiValidator.containsEmoji(emoji)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(emoji, "skintone_test")
            }
        }
    }

    @Test
    fun zeroEmoji_zwjSequences_rejected() {
        val zwjSequences = listOf(
            "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67", // Family: Man, Woman, Girl
            "\uD83D\uDC69\u200D\uD83D\uDCBB",                   // Woman technologist
            "\uD83D\uDC68\u200D\u2764\uFE0F\u200D\uD83D\uDC68"  // Couple with heart
        )
        for (seq in zwjSequences) {
            assertThat(ZeroEmojiValidator.containsEmoji(seq)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(seq, "zwj_test")
            }
        }
    }

    @Test
    fun zeroEmoji_dingbats_rejected() {
        val dingbats = listOf(
            "\u2702", // Scissors
            "\u2705", // White heavy check mark
            "\u270A", // Raised fist
            "\u2728", // Sparkles
            "\u27B0", // Curly loop
            "CutPro \u2705 Verified"
        )
        for (d in dingbats) {
            assertThat(ZeroEmojiValidator.containsEmoji(d)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(d, "dingbat_test")
            }
        }
    }

    @Test
    fun zeroEmoji_variationSelectors_rejected() {
        val variationSelectors = listOf(
            "\uFE0F",             // Variation Selector-16
            "\u2764\uFE0F",       // Red Heart (Heavy Black Heart + VS-16)
            "\u260E\uFE0F",       // Telephone with VS-16
            "Title\uFE0F"
        )
        for (vs in variationSelectors) {
            assertThat(ZeroEmojiValidator.containsEmoji(vs)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(vs, "vs_test")
            }
        }
    }

    @Test
    fun zeroEmoji_flags_regionalIndicators_rejection() {
        // Unicode regional indicators for national flags: U+1F1E6 through U+1F1FF
        val flagUs = "\uD83C\uDDFA\uD83C\uDDF8" // US Flag
        val flagBd = "\uD83C\uDDE7\uD83C\uDDE9" // BD Flag
        val regionalIndicatorAlone = "\uD83C\uDDE6" // Regional Indicator Symbol Letter A

        // Under CutPro zero-emoji mandate, flag emojis and regional indicators must be detected and rejected
        val detectedUs = ZeroEmojiValidator.containsEmoji(flagUs)
        val detectedBd = ZeroEmojiValidator.containsEmoji(flagBd)
        val detectedAlone = ZeroEmojiValidator.containsEmoji(regionalIndicatorAlone)

        assertThat(detectedUs).isTrue()
        assertThat(detectedBd).isTrue()
        assertThat(detectedAlone).isTrue()

        assertThrows(IllegalArgumentException::class.java) {
            ZeroEmojiValidator.validateNoEmoji(flagUs, "flag_us")
        }
    }

    // =========================================================================
    // 2. Template Parameter Fuzzing
    // =========================================================================

    @Test
    fun templateParameters_emptyStrings_rejectedWhenDisallowed() {
        val def = ParameterDefinition(
            key = "title",
            displayName = "Title",
            type = ParameterType.STRING,
            defaultValue = "Default Title",
            disallowEmpty = true
        )
        assertThrows(IllegalArgumentException::class.java) {
            def.validate("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            def.validate("   ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            def.validate("\t\n\r")
        }
    }

    @Test
    fun templateParameters_10000CharStrings_boundedThrows_unboundedEvaluates() {
        val boundedDef = ParameterDefinition(
            key = "title",
            displayName = "Title",
            type = ParameterType.STRING,
            defaultValue = "Title",
            maxLength = 40
        )
        val hugeString = "A".repeat(10_000)
        assertThrows(IllegalArgumentException::class.java) {
            boundedDef.validate(hugeString)
        }

        // For unbounded strings, evaluation must not crash with StackOverflow or OutOfMemory
        val unboundedDef = ParameterDefinition(
            key = "notes",
            displayName = "Notes",
            type = ParameterType.STRING,
            defaultValue = "Notes"
        )
        val validated = unboundedDef.validate(hugeString)
        assertThat(validated).isEqualTo(hugeString)
    }

    @Test
    fun templateParameters_negativeValues_rejectedWhenMinValueSet() {
        val floatDef = ParameterDefinition(
            key = "scale",
            displayName = "Scale",
            type = ParameterType.FLOAT,
            defaultValue = 1.0f,
            minValue = 0.1f,
            maxValue = 3.0f
        )
        assertThrows(IllegalArgumentException::class.java) {
            floatDef.validate(-1.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            floatDef.validate(-0.0001f)
        }

        val intDef = ParameterDefinition(
            key = "count",
            displayName = "Count",
            type = ParameterType.INT,
            defaultValue = 10,
            minValue = 0,
            maxValue = 100
        )
        assertThrows(IllegalArgumentException::class.java) {
            intDef.validate(-1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            intDef.validate(-9999)
        }
    }

    @Test
    fun templateParameters_nanAndInfinity_rejected() {
        val floatDef = ParameterDefinition(
            key = "intensity",
            displayName = "Intensity",
            type = ParameterType.FLOAT,
            defaultValue = 0.5f,
            minValue = 0.0f,
            maxValue = 1.0f
        )

        // NaN must be rejected and not silently returned
        assertThrows(IllegalArgumentException::class.java) {
            floatDef.validate(Float.NaN)
        }

        // Positive and negative infinity must be rejected
        assertThrows(IllegalArgumentException::class.java) {
            floatDef.validate(Float.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            floatDef.validate(Float.NEGATIVE_INFINITY)
        }
    }

    @Test
    fun templateParameters_fuzzAll21Templates_adversarialBoundsCheck() {
        val renderer = MotionGraphicsCanvasRenderer()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        for (tpl in TemplateRegistry.getAllTemplates()) {
            val schema = tpl.schema

            // Test 1: Pass empty map -> schema should populate default parameters
            val defaults = schema.validate(emptyMap())
            assertThat(defaults).isNotEmpty()

            // Test 2: Evaluate at zero, mid, and duration timestamps with defaults
            val dur = tpl.defaultDurationUs
            for (t in listOf(0L, dur / 2, dur)) {
                val state = tpl.evaluate(defaults, t, 1920, 1080)
                assertThat(state).isNotNull()
                tpl.render(canvas, defaults, t, 1920, 1080)
            }

            // Test 3: Pass out-of-range parameters to definitions that have bounds
            for (def in schema.definitions) {
                val maxVal = def.maxValue
                if (def.type == ParameterType.FLOAT && maxVal != null) {
                    val excessValue = maxVal.toFloat() + 1000.0f
                    assertThrows(IllegalArgumentException::class.java) {
                        def.validate(excessValue)
                    }
                }
                val minVal = def.minValue
                if (def.type == ParameterType.INT && minVal != null) {
                    val deficitValue = minVal.toInt() - 1000
                    assertThrows(IllegalArgumentException::class.java) {
                        def.validate(deficitValue)
                    }
                }
            }
        }
    }

    // =========================================================================
    // 3. Subtitle Parser Fuzzing
    // =========================================================================

    @Test
    fun srt_truncatedAndMalformedFiles_handledGracefully() {
        val corruptedInputs = listOf(
            "",
            "   \n\n   \t  ",
            "1\n00:00:01,000 --> 00:00:02,000", // No text lines
            "1\n00:00:01,000 -->",              // Truncated arrow line
            "1\nINVALID TIME --> 00:00:04,000\nText",
            "00:00:05,000 --> 00:00:02,000\nReversed timecode", // endUs < startUs
            "Not a subtitle file at all\nRandom text payload",
            "1\n00:00:01,000 --> 00:00:02,000\n   \n\n" // Blank text
        )

        for (corrupt in corruptedInputs) {
            val cues = SrtParser.parse(corrupt)
            // Must return empty or filtered list without throwing uncaught exceptions
            assertThat(cues).isNotNull()
        }
    }

    @Test
    fun srt_missingIndices_parsesCuesCorrectly() {
        val noIndexSrt = """
            00:00:01,000 --> 00:00:03,000
            First subtitle without index number

            00:00:04,500 --> 00:00:07,000
            Second subtitle without index number
        """.trimIndent()

        val cues = SrtParser.parse(noIndexSrt)
        assertThat(cues).hasSize(2)
        assertThat(cues[0].startTimeUs).isEqualTo(1_000_000L)
        assertThat(cues[0].endTimeUs).isEqualTo(3_000_000L)
        assertThat(cues[0].text).isEqualTo("First subtitle without index number")
        assertThat(cues[1].startTimeUs).isEqualTo(4_500_000L)
        assertThat(cues[1].text).isEqualTo("Second subtitle without index number")
    }

    @Test
    fun srt_mixedLineEndings_parsesIdenticalToLf() {
        val lf = "1\n00:00:01,000 --> 00:00:03,000\nLine A\nLine B\n\n2\n00:00:04,000 --> 00:00:05,000\nLine C\n"
        val crlf = lf.replace("\n", "\r\n")
        val cr = lf.replace("\n", "\r")
        val mixed = "1\r\n00:00:01,000 --> 00:00:03,000\rLine A\nLine B\r\n\r2\n00:00:04,000 --> 00:00:05,000\r\nLine C\n"

        val cuesLf = SrtParser.parse(lf)
        val cuesCrlf = SrtParser.parse(crlf)
        val cuesCr = SrtParser.parse(cr)
        val cuesMixed = SrtParser.parse(mixed)

        assertThat(cuesCrlf).hasSize(cuesLf.size)
        assertThat(cuesCr).hasSize(cuesLf.size)
        assertThat(cuesMixed).hasSize(cuesLf.size)

        for (i in cuesLf.indices) {
            assertThat(cuesMixed[i].startTimeUs).isEqualTo(cuesLf[i].startTimeUs)
            assertThat(cuesMixed[i].endTimeUs).isEqualTo(cuesLf[i].endTimeUs)
            assertThat(cuesMixed[i].text).isEqualTo(cuesLf[i].text)
        }
    }

    @Test
    fun srt_deeplyNestedHtml_strippedCleanly() {
        val nestedTags = buildString {
            append("1\n00:00:01,000 --> 00:00:02,000\n")
            repeat(50) { append("<b><i><u><font color=\"#ff0000\">") }
            append("Deeply Nested Clean Text")
            repeat(50) { append("</font></u></i></b>") }
        }

        val cues = SrtParser.parse(nestedTags)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("Deeply Nested Clean Text")
    }

    @Test
    fun srt_largeFile_stressTest() {
        val largeSrt = buildString {
            for (i in 1..2_000) {
                val startSec = i * 2
                val endSec = startSec + 1
                appendLine(i)
                appendLine("%02d:%02d:%02d,000 --> %02d:%02d:%02d,500".format(
                    startSec / 3600, (startSec % 3600) / 60, startSec % 60,
                    endSec / 3600, (endSec % 3600) / 60, endSec % 60
                ))
                appendLine("Stress test caption payload line for cue number $i")
                appendLine()
            }
        }

        val startTime = System.currentTimeMillis()
        val cues = SrtParser.parse(largeSrt)
        val elapsed = System.currentTimeMillis() - startTime

        assertThat(cues).hasSize(2_000)
        assertThat(elapsed).isLessThan(2_000L) // Must parse in under 2 seconds
    }

    @Test
    fun srt_roundtripFidelity() {
        val originalCues = listOf(
            CaptionCue(startTimeUs = 1_000_000L, endTimeUs = 3_000_000L, text = "First roundtrip cue"),
            CaptionCue(startTimeUs = 4_500_000L, endTimeUs = 7_250_000L, text = "Second roundtrip cue with multiple words"),
            CaptionCue(startTimeUs = 10_000_000L, endTimeUs = 12_000_000L, text = "Final caption cue")
        )

        val exported = SrtParser.export(originalCues)
        val reParsed = SrtParser.parse(exported)

        assertThat(reParsed).hasSize(originalCues.size)
        for (i in originalCues.indices) {
            assertThat(reParsed[i].startTimeUs).isEqualTo(originalCues[i].startTimeUs)
            assertThat(reParsed[i].endTimeUs).isEqualTo(originalCues[i].endTimeUs)
            assertThat(reParsed[i].text).isEqualTo(originalCues[i].text)
        }
    }

    @Test
    fun webVtt_truncatedAndMalformedFiles_handledGracefully() {
        val corruptVtt = listOf(
            "",
            "WEBVTT\n\n",
            "WEBVTT\n00:01.000 -->\nNo end time",
            "WEBVTT\n00:05.000 --> 00:02.000\nReversed time",
            "00:01.000 --> 00:03.000\nMissing WEBVTT header still parses",
            "WEBVTT\n00:01.000 --> 00:03.000 position:10%,line:90% align:left\nCue with settings"
        )

        for (vtt in corruptVtt) {
            val cues = WebVttParser.parse(vtt)
            assertThat(cues).isNotNull()
        }

        // Verify cue with settings parses clean text
        val settingsVtt = "WEBVTT\n\n00:01.000 --> 00:03.000 position:10%,line:90% align:left\nSettings text\n"
        val cuesWithSettings = WebVttParser.parse(settingsVtt)
        assertThat(cuesWithSettings).hasSize(1)
        assertThat(cuesWithSettings[0].text).isEqualTo("Settings text")
    }

    @Test
    fun webVtt_mixedLineEndings_andDeeplyNestedTags() {
        val vttContent = "WEBVTT\r\n\r\n00:01.000 --> 00:03.000\r\n<v Roger><c.loud><b>Nested VTT text</b></c></v>\r\n"
        val cues = WebVttParser.parse(vttContent)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("Nested VTT text")
    }

    @Test
    fun webVtt_roundtripFidelity() {
        val originalCues = listOf(
            CaptionCue(startTimeUs = 2_000_000L, endTimeUs = 5_000_000L, text = "WebVTT Roundtrip Alpha"),
            CaptionCue(startTimeUs = 6_000_000L, endTimeUs = 9_500_000L, text = "WebVTT Roundtrip Beta")
        )

        val exported = WebVttParser.export(originalCues)
        assertThat(exported).startsWith("WEBVTT")

        val reParsed = WebVttParser.parse(exported)
        assertThat(reParsed).hasSize(originalCues.size)
        for (i in originalCues.indices) {
            assertThat(reParsed[i].startTimeUs).isEqualTo(originalCues[i].startTimeUs)
            assertThat(reParsed[i].endTimeUs).isEqualTo(originalCues[i].endTimeUs)
            assertThat(reParsed[i].text).isEqualTo(originalCues[i].text)
        }
    }

    // =========================================================================
    // 4. Karaoke Timing Stress
    // =========================================================================

    @Test
    fun karaoke_zeroDurationCues() {
        val zeroCue = CaptionCue(
            startTimeUs = 5_000_000L,
            endTimeUs = 5_000_000L,
            text = "Instantaneous Flash",
            words = listOf(
                TimedWord("Instantaneous", 5_000_000L, 5_000_000L),
                TimedWord("Flash", 5_000_000L, 5_000_000L)
            )
        )
        val cues = listOf(zeroCue)

        // Exact timestamp match
        val activeAtInstant = KaraokeEngine.findActiveCue(cues, 5_000_000L)
        assertThat(activeAtInstant).isNotNull()

        // 1 microsecond before and after must not match
        assertThat(KaraokeEngine.findActiveCue(cues, 4_999_999L)).isNull()
        assertThat(KaraokeEngine.findActiveCue(cues, 5_000_001L)).isNull()

        // calculateWordProgress must not divide by zero
        val progress = KaraokeEngine.calculateWordProgress(zeroCue.words[0], 5_000_000L)
        assertThat(progress).isEqualTo(1.0f)
    }

    @Test
    fun karaoke_microsecondBoundarySweeps() {
        val cue = CaptionCue(
            startTimeUs = 1_000_000L,
            endTimeUs = 2_000_000L,
            text = "Alpha Beta",
            words = listOf(
                TimedWord("Alpha", 1_000_000L, 1_500_000L),
                TimedWord("Beta", 1_500_000L, 2_000_000L)
            )
        )
        val cues = listOf(cue)

        // Microsecond boundary sweeps around start
        assertThat(KaraokeEngine.findActiveCue(cues, 999_999L)).isNull()
        assertThat(KaraokeEngine.findActiveCue(cues, 1_000_000L)).isEqualTo(cue)
        assertThat(KaraokeEngine.findActiveCue(cues, 1_000_001L)).isEqualTo(cue)

        // Microsecond boundary sweeps around mid
        assertThat(KaraokeEngine.findActiveWord(cue, 1_499_999L)?.word).isEqualTo("Alpha")
        assertThat(KaraokeEngine.findActiveWord(cue, 1_500_000L)?.word).isEqualTo("Alpha")
        assertThat(KaraokeEngine.findActiveWord(cue, 1_500_001L)?.word).isEqualTo("Beta")

        // Microsecond boundary sweeps around end
        assertThat(KaraokeEngine.findActiveCue(cues, 1_999_999L)).isEqualTo(cue)
        assertThat(KaraokeEngine.findActiveCue(cues, 2_000_000L)).isEqualTo(cue)
        assertThat(KaraokeEngine.findActiveCue(cues, 2_000_001L)).isNull()

        // Sweep word progress across duration
        var prevProgress = -1.0f
        for (t in 1_000_000L..1_500_000L step 50_000L) {
            val p = KaraokeEngine.calculateWordProgress(cue.words[0], t)
            assertThat(p).isAtLeast(0.0f)
            assertThat(p).isAtMost(1.0f)
            assertThat(p).isAtLeast(prevProgress)
            prevProgress = p
        }
    }

    @Test
    fun karaoke_corruptedTimestamps_handledSafely() {
        val corruptCue = CaptionCue(
            startTimeUs = 1_000_000L,
            endTimeUs = 2_000_000L,
            text = "Corrupt Timing",
            words = listOf(
                TimedWord("Reversed", 1_500_000L, 1_200_000L), // end < start
                TimedWord("Early", 500_000L, 800_000L),        // before cue start
                TimedWord("Late", 2_500_000L, 3_000_000L)      // after cue end
            )
        )

        // Reversed duration returns 1.0f safely without negative progress or exception
        val revProgress = KaraokeEngine.calculateWordProgress(corruptCue.words[0], 1_300_000L)
        assertThat(revProgress).isEqualTo(1.0f)

        // Query timestamp before start clamps to 0.0f
        val earlyProgress = KaraokeEngine.calculateWordProgress(corruptCue.words[1], 400_000L)
        assertThat(earlyProgress).isEqualTo(0.0f)

        // Query timestamp after end clamps to 1.0f
        val lateProgress = KaraokeEngine.calculateWordProgress(corruptCue.words[2], 3_500_000L)
        assertThat(lateProgress).isEqualTo(1.0f)
    }

    @Test
    fun karaoke_extremeTimestamps_noOverflowOrCrash() {
        val cue = CaptionCue(
            startTimeUs = 1_000_000L,
            endTimeUs = 2_000_000L,
            text = "Normal Cue",
            words = listOf(TimedWord("Normal", 1_000_000L, 2_000_000L))
        )
        val cues = listOf(cue)

        assertThat(KaraokeEngine.findActiveCue(cues, Long.MIN_VALUE)).isNull()
        assertThat(KaraokeEngine.findActiveCue(cues, -1_000_000L)).isNull()
        assertThat(KaraokeEngine.findActiveCue(cues, Long.MAX_VALUE)).isNull()

        val negProgress = KaraokeEngine.calculateWordProgress(cue.words[0], -999_999L)
        assertThat(negProgress).isEqualTo(0.0f)

        val maxProgress = KaraokeEngine.calculateWordProgress(cue.words[0], Long.MAX_VALUE)
        assertThat(maxProgress).isEqualTo(1.0f)
    }

    @Test
    fun subtitleCanvasRenderer_stressWithMalformedStylesAndEmptyCues() {
        val renderer = SubtitleCanvasRenderer()
        val bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Zero/negative viewport size must return cleanly
        renderer.renderAt(canvas, 0, 720, 1_000_000L, emptyList())
        renderer.renderAt(canvas, 1280, 0, 1_000_000L, emptyList())
        renderer.renderAt(canvas, -100, -100, 1_000_000L, emptyList())

        // 2. Empty cues list must return cleanly
        renderer.renderAt(canvas, 1280, 720, 1_000_000L, emptyList())

        // 3. Render active cue with extreme styles
        val extremeStyle = CaptionStyle(
            fontSizeSp = 200f,
            strokeWidthPx = 50f,
            activeWordScaleFactor = 3.0f,
            shadowBlurPx = 100f
        )
        val cue = CaptionCue(
            startTimeUs = 1_000_000L,
            endTimeUs = 2_000_000L,
            text = "Stress Test Caption Rendering",
            customStyle = extremeStyle,
            words = listOf(
                TimedWord("Stress", 1_000_000L, 1_250_000L),
                TimedWord("Test", 1_250_000L, 1_500_000L),
                TimedWord("Caption", 1_500_000L, 1_750_000L),
                TimedWord("Rendering", 1_750_000L, 2_000_000L)
            )
        )
        renderer.renderAt(canvas, 1280, 720, 1_500_000L, listOf(cue))
    }

    // =========================================================================
    // 5. Extended Adversarial Stress Suite (challenger_m4_2_v2)
    // =========================================================================

    @Test
    fun zeroEmoji_flags_exhaustivePairsAndStandaloneIndicators() {
        val flagPairs = listOf(
            "\uD83C\uDDFA\uD83C\uDDF8", // US
            "\uD83C\uDDE7\uD83C\uDDE9", // BD
            "\uD83C\uDDEC\uD83C\uDDE7", // GB
            "\uD83C\uDDEF\uD83C\uDDF5", // JP
            "\uD83C\uDDEB\uD83C\uDDF7", // FR
            "\uD83C\uDDE9\uD83C\uDDEA", // DE
            "\uD83C\uDDEA\uD83C\uDDF8", // ES
            "\uD83C\uDDEE\uD83C\uDDF3", // IN
            "\uD83C\uDDE8\uD83C\uDDE6", // CA
            "\uD83C\uDDE6\uD83C\uDDFA"  // AU
        )
        for (flag in flagPairs) {
            assertThat(ZeroEmojiValidator.containsEmoji(flag)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(flag, "flag_test")
            }
        }

        // Exhaustive test: all 26 standalone regional indicator symbols (U+1F1E6 to U+1F1FF)
        for (cp in 0x1F1E6..0x1F1FF) {
            val indicator = String(Character.toChars(cp))
            assertThat(ZeroEmojiValidator.containsEmoji(indicator)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(indicator, "indicator_$cp")
            }
        }
    }

    @Test
    fun zeroEmoji_enclosedAlphanumericsAndSymbols() {
        val enclosed = listOf(
            String(Character.toChars(0x1F100)), // Digit zero in comma
            String(Character.toChars(0x1F130)), // Squared Latin Capital Letter A
            String(Character.toChars(0x1F192)), // Squared COOL
            String(Character.toChars(0x1F197)), // Squared OK
            String(Character.toChars(0x1F198)), // Squared SOS
            String(Character.toChars(0x1F199)), // Squared UP
            String(Character.toChars(0x1F19A))  // Squared VS
        )
        for (enc in enclosed) {
            assertThat(ZeroEmojiValidator.containsEmoji(enc)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(enc, "enclosed_test")
            }
        }
    }

    @Test
    fun zeroEmoji_mahjongDominoesPlayingCards() {
        val tilesAndCards = listOf(
            String(Character.toChars(0x1F004)), // Mahjong Red Dragon
            String(Character.toChars(0x1F031)), // Domino Tile Horizontal-00-01
            String(Character.toChars(0x1F0A1)), // Playing Card Ace of Spades
            String(Character.toChars(0x1F0CF))  // Playing Card Black Joker
        )
        for (item in tilesAndCards) {
            assertThat(ZeroEmojiValidator.containsEmoji(item)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(item, "tiles_cards_test")
            }
        }
    }

    @Test
    fun zeroEmoji_keycapsAndCombiningMarks() {
        val keycaps = listOf(
            "0\uFE0F\u20E3", // Keycap 0
            "1\uFE0F\u20E3", // Keycap 1
            "7\uFE0F\u20E3", // Keycap 7
            "#\uFE0F\u20E3", // Keycap hash
            "*\uFE0F\u20E3", // Keycap star
            "\u20E3",        // Standalone combining enclosing keycap
            "\u20D0",        // Combining left harpoon above
            "\u20E0"         // Combining enclosing circle backslash
        )
        for (kc in keycaps) {
            assertThat(ZeroEmojiValidator.containsEmoji(kc)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(kc, "keycap_test")
            }
        }
    }

    @Test
    fun zeroEmoji_variationSelectorsStress() {
        // Variation Selectors VS-1 to VS-16 (U+FE00 through U+FE0F)
        for (cp in 0xFE00..0xFE0F) {
            val vs = String(Character.toChars(cp))
            assertThat(ZeroEmojiValidator.containsEmoji(vs)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(vs, "vs_$cp")
            }
        }
        val embeddedVs = listOf("Title\uFE0E", "Title\uFE0F", "A\uFE00B")
        for (str in embeddedVs) {
            assertThat(ZeroEmojiValidator.containsEmoji(str)).isTrue()
            assertThrows(IllegalArgumentException::class.java) {
                ZeroEmojiValidator.validateNoEmoji(str, "embedded_vs")
            }
        }
    }

    @Test
    fun zeroEmoji_naturalLanguagesFalsePositiveCheck() {
        val naturalLanguageTexts = listOf(
            // Bengali
            "\u09AC\u09BE\u0982\u09B2\u09BE \u09AD\u09BE\u09B7\u09BE\u09AF\u09BC \u09AD\u09BF\u09A1\u09BF\u0993 \u098F\u09A1\u09BF\u099F\u09BF\u0982",
            "\u09AF\u09C1\u0995\u09CD\u09A4\u09AC\u09B0\u09CD\u09A3 \u0995\u09CD\u09B7 \u099C\u09CD\u099E \u09B9\u09CD\u09AE \u09AC\u09CD\u09B2 \u09B8\u09CD\u09AA\u09CD\u09B0\u09BF\u0982",
            "\u09E6\u09E7\u09E8\u09E9\u09EA\u09EB\u09EC\u09ED\u09EE\u09EF\u0964",
            // Arabic
            "\u0645\u0631\u062D\u0628\u0627 \u0628\u0643\u0645 \u0641\u064A \u0645\u062D\u0631\u0631 \u0627\u0644\u0641\u064A\u062F\u064A\u0648 CutPro",
            "\u0627\u0644\u0652\u0639\u064E\u0631\u064E\u0628\u0650\u064A\u064E\u0651\u0629\u064F \u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669\u060C \u061F",
            // Chinese
            "\u4F60\u597D\u4E16\u754C \u6B22\u8FCE\u4F7F\u7528CutPro\u89C6\u9891\u7F16\u8F91\u5668",
            "\u6B61\u8FCE\u4F7F\u7528CutPro\u8996\u983B\u526A\u8F2F\uFF0C\u5C08\u696D\u3001\u5F37\u5927\uFF01\u300A\u6E2C\u8A66\u300B",
            // Japanese
            "\u3053\u3093\u306B\u3061\u306F\u4E16\u754C\uFF01 \u30AB\u30C3\u30C8\u30D7\u30ED \u30A8\u30C7\u30A3\u30BF\u30FC",
            "\u52D5\u753B\u7DE8\u96C6\u30BD\u30D5\u30C8\u30A6\u30A7\u30A2\u3001\u6700\u9AD8\u306E\u30D1\u30D5\u30A9\u30FC\u30DE\u30F3\u30B9\u3002",
            // Russian
            "\u041F\u0440\u043E\u0444\u0435\u0441\u0441\u0438\u043E\u043D\u0430\u043B\u044C\u043D\u044B\u0439 \u0432\u0438\u0434\u0435\u043E\u0440\u0435\u0434\u0430\u043A\u0442\u043E\u0440 CutPro.",
            "\u0421\u044A\u0435\u0448\u044C \u0436\u0435 \u0435\u0449\u0451 \u044D\u0442\u0438\u0445 \u043C\u044F\u0433\u043A\u0438\u0445 \u0444\u0440\u0430\u043D\u0446\u0443\u0437\u0441\u043A\u0438\u0445 \u0431\u0443\u043B\u043E\u043A, \u0434\u0430 \u0432\u044B\u043F\u0435\u0439 \u0447\u0430\u044E.",
            // Spanish
            "\u00A1Hola, se\u00F1or! \u00BFC\u00F3mo est\u00E1s? El ni\u00F1o come pi\u00F1a y canta una canci\u00F3n en el a\u00F1o 2026.",
            // French
            "Bonjour le monde! \u00C7a va tr\u00E8s bien, merci. \u00C0 bient\u00F4t \u00E0 No\u00EBl dans la for\u00EAt na\u00EFve d'\u00E9t\u00E9.",
            // German
            "Guten Tag! Die gr\u00F6\u00DFte Stra\u00DFe der Welt f\u00FChrt nach M\u00FCnchen. \u00C4pfel, \u00DCber, \u00D6l, s\u00FC\u00DF."
        )

        for (text in naturalLanguageTexts) {
            assertThat(ZeroEmojiValidator.containsEmoji(text)).isFalse()
            ZeroEmojiValidator.validateNoEmoji(text, "natural_lang")
        }
    }

    @Test
    fun parameterDefinition_nanAndInfinityStress_floatsDoublesAndStrings() {
        val floatDef = ParameterDefinition(
            key = "opacity",
            displayName = "Opacity",
            type = ParameterType.FLOAT,
            defaultValue = 1.0f,
            minValue = 0.0f,
            maxValue = 1.0f
        )
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate(Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate(Float.NEGATIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate(Double.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate(Double.NEGATIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate("NaN") }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate("nan") }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate("  NaN  ") }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate("Infinity") }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate("+Infinity") }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate("-Infinity") }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate("1e309") }
        assertThrows(IllegalArgumentException::class.java) { floatDef.validate("not_a_number") }

        // Unbounded float definition
        val unboundedFloat = ParameterDefinition(
            key = "unbounded",
            displayName = "Unbounded",
            type = ParameterType.FLOAT,
            defaultValue = 0.0f
        )
        assertThrows(IllegalArgumentException::class.java) { unboundedFloat.validate(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { unboundedFloat.validate(Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { unboundedFloat.validate("NaN") }
        assertThrows(IllegalArgumentException::class.java) { unboundedFloat.validate("Infinity") }
    }

    @Test
    fun parameterDefinition_integerNanCoercionAndBoundsStress() {
        val intDef = ParameterDefinition(
            key = "count",
            displayName = "Count",
            type = ParameterType.INT,
            defaultValue = 10,
            minValue = 0,
            maxValue = 100
        )
        // NaN must NOT silently coerce to 0
        assertThrows(IllegalArgumentException::class.java) { intDef.validate(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { intDef.validate(Double.NaN) }
        // Infinity must NOT silently coerce to Int.MAX_VALUE / Int.MIN_VALUE
        assertThrows(IllegalArgumentException::class.java) { intDef.validate(Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { intDef.validate(Float.NEGATIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { intDef.validate("NaN") }
        assertThrows(IllegalArgumentException::class.java) { intDef.validate("Infinity") }
        assertThrows(IllegalArgumentException::class.java) { intDef.validate("-Infinity") }

        // Numbers exceeding Int range
        assertThrows(IllegalArgumentException::class.java) { intDef.validate(1e15) }
        assertThrows(IllegalArgumentException::class.java) { intDef.validate(-1e15) }
        assertThrows(IllegalArgumentException::class.java) { intDef.validate("99999999999999999999") }
    }

    @Test
    fun parameterDefinition_colorNanAndMalformedStress() {
        val colorDef = ParameterDefinition(
            key = "color",
            displayName = "Color",
            type = ParameterType.COLOR,
            defaultValue = 0xFFFFFFFFL
        )
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate(Float.NaN) }
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate(Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate(Float.NEGATIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate("NaN") }
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate("Infinity") }
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate("not_a_color") }
        assertThrows(IllegalArgumentException::class.java) { colorDef.validate("ZZZZZZ") }

        // Valid colors pass
        assertThat(colorDef.validate(0xFFFF0000L)).isEqualTo(0xFFFF0000L)
        assertThat(colorDef.validate("#FF00FF00")).isEqualTo(0xFF00FF00L)
        assertThat(colorDef.validate("0000FFFF")).isEqualTo(0x0000FFFFL)
    }

    @Test
    fun parameterDefinition_constructorInvariantsStress() {
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("k", "d", ParameterType.FLOAT, 1.0f, minValue = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("k", "d", ParameterType.FLOAT, 1.0f, maxValue = Float.NaN)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("k", "d", ParameterType.FLOAT, 1.0f, minValue = Float.POSITIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("k", "d", ParameterType.FLOAT, 1.0f, maxValue = Float.NEGATIVE_INFINITY)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("k", "d", ParameterType.FLOAT, 1.0f, minValue = 10.0f, maxValue = 5.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("k", "d", ParameterType.STRING, "text", minLength = -1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("k", "d", ParameterType.STRING, "text", minLength = 10, maxLength = 5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("k", "d", ParameterType.FLOAT, 50.0f, minValue = 0.0f, maxValue = 10.0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ParameterDefinition("k", "d", ParameterType.STRING, "Title \uD83D\uDE00")
        }
    }

    @Test
    fun subtitles_multilineWrapping_emptyAndWhitespaceStrings() {
        val renderer = SubtitleCanvasRenderer()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Empty string cue
        val emptyCue = CaptionCue(1_000_000L, 2_000_000L, "")
        renderer.renderAt(canvas, 1920, 1080, 1_500_000L, listOf(emptyCue))

        // Whitespace-only cue
        val whitespaceCue = CaptionCue(1_000_000L, 2_000_000L, "   \t\n   \r\n   ")
        renderer.renderAt(canvas, 1920, 1080, 1_500_000L, listOf(whitespaceCue))

        // Multi-line blank cues
        val blankLinesCue = CaptionCue(1_000_000L, 2_000_000L, "\n\n\n")
        renderer.renderAt(canvas, 1920, 1080, 1_500_000L, listOf(blankLinesCue))
    }

    @Test
    fun subtitles_multilineWrapping_10000CharStress() {
        val renderer = SubtitleCanvasRenderer()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val hugeText = "StressWord ".repeat(1000) // 11,000 characters
        val hugeCue = CaptionCue(1_000_000L, 2_000_000L, hugeText)

        // Renders without StackOverflowError or OOM or IndexOutOfBoundsException
        renderer.renderAt(canvas, 1920, 1080, 1_500_000L, listOf(hugeCue))
    }

    @Test
    fun subtitles_corruptSrtAndWebVtt_adversarialPayloads() {
        val corruptSrts = listOf(
            "\uFEFF1\n00:00:01,000 --> 00:00:02,000\nBOM header test",
            "1\n00:00:10,000 --> 00:00:05,000\nReversed timecodes",
            "1\n-00:00:01,000 --> 00:00:02,000\nNegative timecode",
            "1\n99:99:99,999 --> 99:99:99,999\nExtreme timecodes",
            "1\n00:00:01,000 --> 00:00:02,000\n<b><i><u>Unclosed tags",
            "1\n00:00:01,000 --> 00:00:02,000\nText with null\u0000byte and control\u0007chars",
            "\n\n\n\n\n\n",
            "1\nMalformed line without arrow"
        )
        for (srt in corruptSrts) {
            val parsed = SrtParser.parse(srt)
            assertThat(parsed).isNotNull()
        }

        val corruptVtts = listOf(
            "WEBVTT\n\n00:00:10.000 --> 00:00:05.000\nReversed VTT",
            "WEBVTT\n\nNOTE comment\n\n00:01.000 --> 00:03.000 line:bad align:invalid\nBad settings",
            "WEBVTT00:01.000 --> 00:02.000\nNo header newline",
            "WEBVTT\n\n00:01,000 --> 00:02,000\nComma instead of period in VTT",
            "WEBVTT\n\n<c.color><b><i>Unclosed VTT tags"
        )
        for (vtt in corruptVtts) {
            val parsed = WebVttParser.parse(vtt)
            assertThat(parsed).isNotNull()
        }
    }

    @Test
    fun subtitles_karaokeWordTracking_acrossWrappedLines() {
        val renderer = SubtitleCanvasRenderer()
        val bitmap = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 18 words wrapping across multiple lines
        val wordList = (1..18).map { "Word$it" }
        val timedWordList = wordList.mapIndexed { idx, w ->
            TimedWord(w, idx * 100_000L, (idx + 1) * 100_000L)
        }
        val multiLineKaraokeCue = CaptionCue(
            startTimeUs = 0L,
            endTimeUs = 1_800_000L,
            text = wordList.joinToString(" "),
            words = timedWordList,
            customStyle = CaptionStyle.KARAOKE_POP
        )

        // Word 3 on Line 1 (time 250_000L -> index 2)
        val idxLine1 = KaraokeEngine.findActiveWordIndex(multiLineKaraokeCue, 250_000L)
        assertThat(idxLine1).isEqualTo(2)
        renderer.renderAt(canvas, 600, 400, 250_000L, listOf(multiLineKaraokeCue))

        // Word 9 on Line 2 (time 850_000L -> index 8)
        val idxLine2 = KaraokeEngine.findActiveWordIndex(multiLineKaraokeCue, 850_000L)
        assertThat(idxLine2).isEqualTo(8)
        renderer.renderAt(canvas, 600, 400, 850_000L, listOf(multiLineKaraokeCue))

        // Word 16 on Line 3 (time 1_550_000L -> index 15)
        val idxLine3 = KaraokeEngine.findActiveWordIndex(multiLineKaraokeCue, 1_550_000L)
        assertThat(idxLine3).isEqualTo(15)
        renderer.renderAt(canvas, 600, 400, 1_550_000L, listOf(multiLineKaraokeCue))

        // After all words (time 2_000_000L -> index -1)
        val idxAfter = KaraokeEngine.findActiveWordIndex(multiLineKaraokeCue, 2_000_000L)
        assertThat(idxAfter).isEqualTo(-1)
        renderer.renderAt(canvas, 600, 400, 2_000_000L, listOf(multiLineKaraokeCue))
    }
}

