package com.cutpro.core.subtitles.parser

import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.TimedWord
import java.util.regex.Pattern

object WebVttParser {

    private val VTT_TIME_PATTERN = Pattern.compile(
        """(?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3})\s*-->\s*(?:(\d{2}):)?(\d{2}):(\d{2})\.(\d{3})"""
    )
    private val HTML_TAG_REGEX = Regex("<[^>]*>")

    fun parse(input: String): List<CaptionCue> {
        val cleanInput = input.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val lines = cleanInput.lines()
        val cues = mutableListOf<CaptionCue>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trim()
            val matcher = VTT_TIME_PATTERN.matcher(line)
            if (matcher.find()) {
                val startHh = matcher.group(1)?.toIntOrNull() ?: 0
                val startMm = matcher.group(2)!!.toInt()
                val startSs = matcher.group(3)!!.toInt()
                val startMs = matcher.group(4)!!.toInt()
                val startUs = (startHh * 3600L + startMm * 60L + startSs) * 1_000_000L + startMs * 1_000L

                val endHh = matcher.group(5)?.toIntOrNull() ?: 0
                val endMm = matcher.group(6)!!.toInt()
                val endSs = matcher.group(7)!!.toInt()
                val endMs = matcher.group(8)!!.toInt()
                val endUs = (endHh * 3600L + endMm * 60L + endSs) * 1_000_000L + endMs * 1_000L

                i++
                val textBuilder = StringBuilder()
                while (i < lines.size && lines[i].isNotBlank()) {
                    textBuilder.appendLine(lines[i].trim())
                    i++
                }
                val rawText = textBuilder.toString().trim()
                if (rawText.isNotEmpty() && endUs >= startUs) {
                    val cleanText = rawText.replace(HTML_TAG_REGEX, "").trim()
                    if (cleanText.isNotEmpty()) {
                        val words = generateEstimatedWordTimings(cleanText, startUs, endUs)
                        cues.add(CaptionCue(startTimeUs = startUs, endTimeUs = endUs, text = cleanText, words = words))
                    }
                }
            } else {
                i++
            }
        }

        return cues
    }

    fun export(cues: List<CaptionCue>): String = buildString {
        appendLine("WEBVTT")
        appendLine()
        cues.sortedBy { it.startTimeUs }.forEach { cue ->
            appendLine("${formatVttTime(cue.startTimeUs)} --> ${formatVttTime(cue.endTimeUs)}")
            appendLine(cue.text)
            appendLine()
        }
    }

    private fun formatVttTime(us: Long): String {
        val totalMs = us / 1000L
        val ms = totalMs % 1000L
        val totalSec = totalMs / 1000L
        val ss = totalSec % 60L
        val totalMin = totalSec / 60L
        val mm = totalMin % 60L
        val hh = totalMin / 60L
        return "%02d:%02d:%02d.%03d".format(hh, mm, ss, ms)
    }

    private fun generateEstimatedWordTimings(text: String, startUs: Long, endUs: Long): List<TimedWord> {
        val tokens = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        val totalDuration = (endUs - startUs).coerceAtLeast(0L)
        val slice = if (tokens.isNotEmpty()) totalDuration / tokens.size else 0L

        return tokens.mapIndexed { idx, word ->
            TimedWord(
                word = word,
                startOffsetUs = startUs + (idx * slice),
                endOffsetUs = if (idx == tokens.lastIndex) endUs else startUs + ((idx + 1) * slice)
            )
        }
    }
}
