package com.cutpro.core.common.timecode

import java.util.Locale

data class Timecode(
    val hours: Int,
    val minutes: Int,
    val seconds: Int,
    val frames: Int,
    val fps: Int
) {
    init {
        require(hours >= 0) { "hours cannot be negative" }
        require(minutes in 0..59) { "minutes must be in [0, 59]" }
        require(seconds in 0..59) { "seconds must be in [0, 59]" }
        require(fps > 0) { "fps must be > 0" }
        require(frames in 0 until fps) { "frames ($frames) must be in [0, ${fps - 1}]" }
    }

    fun toUs(): Long {
        val totalSeconds = hours * 3600L + minutes * 60L + seconds
        val frameUs = (frames * 1_000_000L) / fps
        return totalSeconds * 1_000_000L + frameUs
    }

    fun toSmpteString(): String {
        return String.format(Locale.US, "%02d:%02d:%02d:%02d", hours, minutes, seconds, frames)
    }
}

object TimecodeHelper {

    fun fromUs(timeUs: Long, fps: Int): Timecode {
        require(timeUs >= 0L) { "timeUs ($timeUs) cannot be negative" }
        require(fps > 0) { "fps ($fps) must be > 0" }

        val totalSeconds = timeUs / 1_000_000L
        val remainderUs = timeUs % 1_000_000L
        val frames = ((remainderUs * fps + 500_000L) / 1_000_000L).toInt().coerceAtMost(fps - 1)

        val hours = (totalSeconds / 3600L).toInt()
        val minutes = ((totalSeconds % 3600L) / 60L).toInt()
        val seconds = (totalSeconds % 60L).toInt()

        return Timecode(hours, minutes, seconds, frames, fps)
    }

    fun formatSmpte(timeUs: Long, fps: Int): String {
        return fromUs(timeUs, fps).toSmpteString()
    }

    fun formatSrt(timeUs: Long): String {
        require(timeUs >= 0L) { "timeUs cannot be negative" }
        val totalMs = timeUs / 1000L
        val ms = (totalMs % 1000L).toInt()
        val totalSeconds = totalMs / 1000L
        val seconds = (totalSeconds % 60L).toInt()
        val totalMinutes = totalSeconds / 60L
        val minutes = (totalMinutes % 60L).toInt()
        val hours = (totalMinutes / 60L).toInt()

        return String.format(Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, ms)
    }

    fun formatVtt(timeUs: Long): String {
        require(timeUs >= 0L) { "timeUs cannot be negative" }
        val totalMs = timeUs / 1000L
        val ms = (totalMs % 1000L).toInt()
        val totalSeconds = totalMs / 1000L
        val seconds = (totalSeconds % 60L).toInt()
        val totalMinutes = totalSeconds / 60L
        val minutes = (totalMinutes % 60L).toInt()
        val hours = (totalMinutes / 60L).toInt()

        return String.format(Locale.US, "%02d:%02d:%02d.%03d", hours, minutes, seconds, ms)
    }

    fun parseSmpte(smpteString: String, fps: Int): Long {
        require(fps > 0) { "fps must be > 0" }
        val parts = smpteString.trim().split(":")
        require(parts.size == 4) { "Invalid SMPTE timecode format: '$smpteString' (expected HH:MM:SS:FF)" }

        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val s = parts[2].toInt()
        val f = parts[3].toInt()

        return Timecode(h, m, s, f, fps).toUs()
    }

    fun parseSrtTimecode(srtString: String): Long {
        val parts = srtString.trim().split(":", ",")
        require(parts.size == 4) { "Invalid SRT timecode format: '$srtString' (expected HH:MM:SS,mmm)" }

        val h = parts[0].toInt()
        val m = parts[1].toInt()
        val s = parts[2].toInt()
        val ms = parts[3].toInt()

        val totalSeconds = h * 3600L + m * 60L + s
        return totalSeconds * 1_000_000L + ms * 1000L
    }

    fun parseVttTimecode(vttString: String): Long {
        val cleaned = vttString.trim()
        val dotParts = cleaned.split(".")
        require(dotParts.size == 2) { "Invalid VTT timecode format: '$vttString'" }
        val ms = dotParts[1].toInt()

        val timeParts = dotParts[0].split(":")
        val (h, m, s) = when (timeParts.size) {
            2 -> Triple(0, timeParts[0].toInt(), timeParts[1].toInt())
            3 -> Triple(timeParts[0].toInt(), timeParts[1].toInt(), timeParts[2].toInt())
            else -> throw IllegalArgumentException("Invalid VTT timecode components: '$vttString'")
        }

        val totalSeconds = h * 3600L + m * 60L + s
        return totalSeconds * 1_000_000L + ms * 1000L
    }
}
