package com.cutpro.core.motiongraphics.evaluator

import kotlin.math.floor
import kotlin.math.min

object TypewriterSlicer {

    fun slice(
        fullText: String,
        speedCps: Float,
        timestampUs: Long,
        cursorBlinkHz: Float = 2.5f
    ): Pair<String, Boolean> {
        if (speedCps <= 0f || timestampUs <= 0L) {
            val cursorVisible = isCursorVisible(timestampUs, cursorBlinkHz)
            return Pair("", cursorVisible)
        }

        val seconds = timestampUs / 1_000_000.0
        val charsVisible = min(fullText.length, floor(seconds * speedCps).toInt())
        val visibleText = fullText.substring(0, charsVisible)
        val cursorVisible = isCursorVisible(timestampUs, cursorBlinkHz)

        return Pair(visibleText, cursorVisible)
    }

    private fun isCursorVisible(timestampUs: Long, blinkHz: Float): Boolean {
        if (blinkHz <= 0f) return true
        val blinkPeriodUs = (1_000_000.0 / (blinkHz * 2.0)).toLong()
        if (blinkPeriodUs <= 0L) return true
        val cycle = (timestampUs / blinkPeriodUs) % 2
        return cycle == 0L
    }
}
