package com.cutpro.core.ai.stt.bengali

import com.cutpro.core.subtitles.model.TimedWord

/**
 * Utility for handling Bengali Unicode script (\u0980..\u09FF), conjuncts (যুক্তবর্ণ),
 * Danda (।), sentence tokenization, and weighted duration calculation for karaoke cues.
 */
object BengaliScriptHelper {
    const val BENGALI_RANGE_START: Int = 0x0980
    const val BENGALI_RANGE_END: Int = 0x09FF
    const val DANDA_CODE_POINT: Int = 0x0964        // । (Dari / Purna Viram)
    const val DOUBLE_DANDA_CODE_POINT: Int = 0x0965 // ॥ (Double Danda)
    const val BENGALI_VIRAMA: Int = 0x09CD          // ্ (Hasant)

    private val SENTENCE_DELIMITER_REGEX = Regex("(?<=[।॥?!])\\s+|\n+")
    private val WORD_DELIMITER_REGEX = Regex("\\s+")

    fun isBengaliCodePoint(cp: Int): Boolean {
        return (cp in BENGALI_RANGE_START..BENGALI_RANGE_END) ||
            cp == DANDA_CODE_POINT ||
            cp == DOUBLE_DANDA_CODE_POINT
    }

    fun isBengaliText(text: String): Boolean {
        if (text.isEmpty()) return false
        var bengaliChars = 0
        var totalAlpha = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (isBengaliCodePoint(cp)) bengaliChars++
            if (Character.isLetter(cp)) totalAlpha++
            i += Character.charCount(cp)
        }
        return totalAlpha > 0 && (bengaliChars.toFloat() / totalAlpha) >= 0.5f
    }

    fun splitIntoSentences(text: String): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        return clean.split(SENTENCE_DELIMITER_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun splitIntoWords(text: String): List<String> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        return clean.split(WORD_DELIMITER_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun alignWordsToCueDuration(
        words: List<String>,
        cueStartUs: Long,
        cueEndUs: Long
    ): List<TimedWord> {
        if (words.isEmpty()) return emptyList()
        val totalDurationUs = (cueEndUs - cueStartUs).coerceAtLeast(0L)
        val totalWeight = words.sumOf { maxOf(1, it.length) }

        var currentStartUs = cueStartUs
        return words.mapIndexed { index, word ->
            val weight = maxOf(1, word.length)
            val wordDurationUs = if (index == words.lastIndex) {
                cueEndUs - currentStartUs
            } else {
                (totalDurationUs * weight) / totalWeight
            }
            val wordEndUs = (currentStartUs + wordDurationUs).coerceAtMost(cueEndUs)
            val timedWord = TimedWord(
                word = word,
                startOffsetUs = currentStartUs,
                endOffsetUs = wordEndUs
            )
            currentStartUs = wordEndUs
            timedWord
        }
    }
}
