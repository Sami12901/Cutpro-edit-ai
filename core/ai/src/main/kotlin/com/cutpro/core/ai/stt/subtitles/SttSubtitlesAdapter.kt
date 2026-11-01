package com.cutpro.core.ai.stt.subtitles

import com.cutpro.core.ai.stt.model.SupportedLanguage
import com.cutpro.core.motiongraphics.schema.ZeroEmojiValidator
import com.cutpro.core.subtitles.karaoke.KaraokeEngine
import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.CaptionTrack
import com.cutpro.core.subtitles.parser.SrtParser
import com.cutpro.core.subtitles.parser.WebVttParser
import java.util.UUID

/**
 * Adapter converting Speech-to-Text output into domain Caption models,
 * validating zero emojis, exporting to SRT/WebVTT, and coordinating Karaoke word synchronization.
 */
object SttSubtitlesAdapter {

    fun toCaptionTrack(
        cues: List<CaptionCue>,
        language: SupportedLanguage,
        trackName: String = "Captions (${language.displayName})"
    ): CaptionTrack {
        cues.forEach { cue ->
            ZeroEmojiValidator.validateNoEmoji(cue.text, "cue_text")
            cue.words.forEach { word ->
                ZeroEmojiValidator.validateNoEmoji(word.word, "word_text")
            }
        }

        return CaptionTrack(
            id = UUID.randomUUID().toString(),
            name = trackName,
            languageCode = language.code,
            isVisible = true,
            cues = cues
        )
    }

    fun exportToSrt(cues: List<CaptionCue>): String = SrtParser.export(cues)

    fun exportToWebVtt(cues: List<CaptionCue>): String = WebVttParser.export(cues)

    fun findActiveKaraokeWord(cues: List<CaptionCue>, timestampUs: Long): String? {
        val activeCue = KaraokeEngine.findActiveCue(cues, timestampUs) ?: return null
        return KaraokeEngine.findActiveWord(activeCue, timestampUs)?.word
    }
}
