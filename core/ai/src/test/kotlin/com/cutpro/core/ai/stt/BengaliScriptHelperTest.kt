package com.cutpro.core.ai.stt

import com.cutpro.core.ai.stt.bengali.BengaliScriptHelper
import com.cutpro.core.motiongraphics.schema.ZeroEmojiValidator
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BengaliScriptHelperTest {

    @Test
    fun bengaliDetection_validBengaliText_returnsTrue() {
        val bengaliText = "বাংলা ভাষায় ভিডিও এডিটিং"
        assertThat(BengaliScriptHelper.isBengaliText(bengaliText)).isTrue()
    }

    @Test
    fun bengaliDetection_englishText_returnsFalse() {
        val englishText = "Welcome to CutPro video editor"
        assertThat(BengaliScriptHelper.isBengaliText(englishText)).isFalse()
    }

    @Test
    fun sentenceSplitting_dandaAndPunctuation() {
        val text = "কাটপ্রো স্বাগতম। নতুন ভিডিও তৈরি করুন! সহজে।"
        val sentences = BengaliScriptHelper.splitIntoSentences(text)

        assertThat(sentences).hasSize(3)
        assertThat(sentences[0]).isEqualTo("কাটপ্রো স্বাগতম।")
        assertThat(sentences[1]).isEqualTo("নতুন ভিডিও তৈরি করুন!")
        assertThat(sentences[2]).isEqualTo("সহজে।")
    }

    @Test
    fun wordSplitting_conjunctIntegrity() {
        val text = "যুক্তবর্ণ ক্ষ জ্ঞ হ্ম ব্ল"
        val words = BengaliScriptHelper.splitIntoWords(text)

        assertThat(words).hasSize(5)
        assertThat(words[0]).isEqualTo("যুক্তবর্ণ")
        assertThat(words[1]).isEqualTo("ক্ষ")
        assertThat(words[2]).isEqualTo("জ্ঞ")
        assertThat(words[3]).isEqualTo("হ্ম")
        assertThat(words[4]).isEqualTo("ব্ল")
    }

    @Test
    fun wordAlignment_allocatesProportionalDurations() {
        val words = listOf("এ", "আন্তর্জাতিক", "সম্মেলন")
        val cueStartUs = 1_000_000L
        val cueEndUs = 3_000_000L // 2.0s duration

        val timedWords = BengaliScriptHelper.alignWordsToCueDuration(words, cueStartUs, cueEndUs)

        assertThat(timedWords).hasSize(3)

        // First word starts at cueStartUs
        assertThat(timedWords[0].startOffsetUs).isEqualTo(cueStartUs)
        // Last word ends at cueEndUs
        assertThat(timedWords[2].endOffsetUs).isEqualTo(cueEndUs)

        // Continuity check
        assertThat(timedWords[0].endOffsetUs).isEqualTo(timedWords[1].startOffsetUs)
        assertThat(timedWords[1].endOffsetUs).isEqualTo(timedWords[2].startOffsetUs)

        // "আন্তর্জাতিক" (12 chars) must have significantly longer duration than "এ" (1 char)
        val shortWordDuration = timedWords[0].endOffsetUs - timedWords[0].startOffsetUs
        val longWordDuration = timedWords[1].endOffsetUs - timedWords[1].startOffsetUs
        assertThat(longWordDuration).isGreaterThan(shortWordDuration * 3)
    }

    @Test
    fun bengaliText_zeroEmojiCompliance() {
        val testCorpus = listOf(
            "কাটপ্রো ভিডিও এডিটরে স্বাগতম।",
            "সহজে পেশাদার মাল্টিট্র্যাক ভিডিও তৈরি করুন।",
            "সম্পূর্ণ অফলাইন এবং দ্রুতগতির ভিডিও এডিটিং।"
        )
        for (sentence in testCorpus) {
            assertThat(ZeroEmojiValidator.containsEmoji(sentence)).isFalse()
        }
    }
}
