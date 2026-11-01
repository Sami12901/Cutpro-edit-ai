package com.cutpro.core.subtitles.parser

import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.TimedWord
import java.util.regex.Pattern

object SrtParser {

    private val TIME_PATTERN = Pattern.compile(
        """(?:(\d{2}):)?(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(?:(\d{2}):)?(\d{2}):(\d{2})[,.](\d{3})"""
    )
    private val HTML_TAG_REGEX = Regex("<[^>]*>")

    fun parse(input: String): List<CaptionCue> {
        val cleanInput = input.replace("\uFEFF", "").replace("\r\n", "\n").replace("\r", "\n")
        val blocks = cleanInput.split(Regex("\n\\s*\n"))
        val cues = mutableListOf<CaptionCue>()

        for (block in blocks) {
            val lines = block.trim().lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue

            var timeLineIndex = -1
            var matcher = TIME_PATTERN.matcher("")
            for (i in lines.indices) {
                matcher = TIME_PATTERN.matcher(lines[i])
                if (matcher.find()) {
                    timeLineIndex = i
                    break
                }
            }
            if (timeLineIndex == -1) continue

            val startHh = matcher.group(1)?.toIntOrNull() ?: 0
            val startMm = matcher.group(2)!!.toInt()
            val startSs = matcher.group(3)!!.toInt()
            val startMs = matcher.group(4)!!.toInt()

            val endHh = matcher.group(5)?.toIntOrNull() ?: 0
            val endMm = matcher.group(6)!!.toInt()
            val endSs = matcher.group(7)!!.toInt()
            val endMs = matcher.group(8)!!.toInt()

            val startUs = (startHh * 3600L + startMm * 60L + startSs) * 1_000_000L + startMs * 1_000L
            val endUs = (endHh * 3600L + endMm * 60L + endSs) * 1_000_000L + endMs * 1_000L

            if (endUs < startUs) continue

            val textLines = lines.subList(timeLineIndex + 1, lines.size)
            val rawText = textLines.joinToString("\n").replace(HTML_TAG_REGEX, "").trim()
            if (rawText.isEmpty()) continue

            val words = generateEstimatedWordTimings(rawText, startUs, endUs)
            cues.add(CaptionCue(startTimeUs = startUs, endTimeUs = endUs, text = rawText, words = words))
        }

        return cues
    }

    fun export(cues: List<CaptionCue>): String = buildString {
        cues.sortedBy { it.startTimeUs }.forEachIndexed { index, cue ->
            appendLine(index + 1)
            appendLine("${formatTimecode(cue.startTimeUs)} --> ${formatTimecode(cue.endTimeUs)}")
            appendLine(cue.text)
            appendLine()
        }
    }

    private fun formatTimecode(us: Long): String {
        val totalMs = us / 1000L
        val ms = totalMs % 1000L
        val totalSec = totalMs / 1000L
        val ss = totalSec % 60L
        val totalMin = totalSec / 60L
        val mm = totalMin % 60L
        val hh = totalMin / 60L
        return "%02d:%02d:%02d,%03d".format(hh, mm, ss, ms)
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
