package com.cutpro.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TimelineTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: TrackType,
    val zIndex: Int = 0,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val clips: List<TimelineClip> = emptyList()
) {
    val durationUs: Long get() = clips.maxOfOrNull { it.timelineEndUs } ?: 0L

    init {
        require(id.isNotBlank()) { "Track id cannot be blank" }
        require(name.isNotBlank()) { "Track name cannot be blank" }
        require(clips.all { it.trackId == id }) {
            "All clips in track '$name' ($id) must reference this trackId"
        }
    }

    fun findClipAt(timestampUs: Long): TimelineClip? {
        return clips.firstOrNull { it.containsTimelineTimestamp(timestampUs) }
    }

    fun validateNonOverlapping(): Boolean {
        if (clips.size <= 1) return true
        val sorted = clips.sortedBy { it.timelineStartUs }
        for (i in 0 until sorted.size - 1) {
            if (sorted[i].timelineEndUs > sorted[i + 1].timelineStartUs) {
                return false
            }
        }
        return true
    }

    fun withClipAdded(clip: TimelineClip): TimelineTrack {
        require(clip.trackId == id) { "Clip trackId (${clip.trackId}) does not match track ($id)" }
        val updated = (clips + clip).sortedBy { it.timelineStartUs }
        return copy(clips = updated)
    }

    fun withClipRemoved(clipId: String): TimelineTrack {
        return copy(clips = clips.filter { it.id != clipId })
    }

    fun withClipReplaced(clip: TimelineClip): TimelineTrack {
        require(clip.trackId == id) { "Clip trackId (${clip.trackId}) does not match track ($id)" }
        val updated = clips.map { if (it.id == clip.id) clip else it }.sortedBy { it.timelineStartUs }
        return copy(clips = updated)
    }
}
