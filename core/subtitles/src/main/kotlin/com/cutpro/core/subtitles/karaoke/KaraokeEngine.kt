package com.cutpro.core.subtitles.karaoke

import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.TimedWord

object KaraokeEngine {

    fun findActiveCue(cues: List<CaptionCue>, timestampUs: Long): CaptionCue? {
        return cues.firstOrNull { timestampUs in it.startTimeUs..it.endTimeUs }
    }

    fun findActiveWordIndex(cue: CaptionCue, timestampUs: Long): Int {
        if (cue.words.isEmpty()) return -1
        return cue.words.indexOfFirst { timestampUs in it.startOffsetUs..it.endOffsetUs }
    }

    fun findActiveWord(cue: CaptionCue, timestampUs: Long): TimedWord? {
        val idx = findActiveWordIndex(cue, timestampUs)
        return if (idx >= 0) cue.words[idx] else null
    }

    fun calculateWordProgress(word: TimedWord, timestampUs: Long): Float {
        val duration = word.endOffsetUs - word.startOffsetUs
        if (duration <= 0L) return 1.0f
        return ((timestampUs - word.startOffsetUs).toFloat() / duration).coerceIn(0.0f, 1.0f)
    }
}
