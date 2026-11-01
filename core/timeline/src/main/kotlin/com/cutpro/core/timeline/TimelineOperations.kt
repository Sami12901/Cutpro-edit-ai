package com.cutpro.core.timeline

import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack

/**
 * Immutable utility operations for ProjectManifest and multitrack manipulation.
 */
object TimelineOperations {

    /**
     * Recalculates total project duration in microseconds based on the latest end time of all clips.
     */
    fun recalculateDurationUs(project: ProjectManifest): Long {
        val maxEndUs = project.tracks
            .flatMap { it.clips }
            .maxOfOrNull { it.timelineEndUs } ?: 0L
        return maxEndUs
    }

    /**
     * Checks if a clip overlaps with any existing clip in the same track (excluding a specified clip ID).
     */
    fun hasTrackOverlap(
        track: TimelineTrack,
        startUs: Long,
        durationUs: Long,
        excludeClipId: String? = null
    ): Boolean {
        val endUs = startUs + durationUs
        return track.clips
            .filter { excludeClipId == null || it.id != excludeClipId }
            .any { existing ->
                startUs < existing.timelineEndUs && existing.timelineStartUs < endUs
            }
    }

    /**
     * Collects all candidate snap points across the project:
     * - 0L (project start)
     * - Total project duration
     * - Active playhead position
     * - In-points and out-points of all clips across visible tracks
     */
    fun collectCandidateSnapPoints(
        project: ProjectManifest,
        playheadUs: Long? = null,
        excludeClipId: String? = null
    ): List<Long> {
        val points = mutableSetOf<Long>()
        points.add(0L)
        if (project.durationUs > 0L) {
            points.add(project.durationUs)
        }
        if (playheadUs != null && playheadUs >= 0L) {
            points.add(playheadUs)
        }

        for (track in project.tracks) {
            if (!track.isVisible) continue
            for (clip in track.clips) {
                if (excludeClipId != null && clip.id == excludeClipId) continue
                points.add(clip.timelineStartUs)
                points.add(clip.timelineEndUs)
            }
        }

        return points.sorted()
    }

    /**
     * Replaces an existing clip in the project and recalculates duration.
     */
    fun replaceClipInProject(
        project: ProjectManifest,
        trackId: String,
        updatedClip: TimelineClip
    ): ProjectManifest {
        val updatedTracks = project.tracks.map { track ->
            if (track.id == trackId) {
                val newClips = track.clips.map { clip ->
                    if (clip.id == updatedClip.id) updatedClip else clip
                }.sortedBy { it.timelineStartUs }
                track.copy(clips = newClips)
            } else {
                track
            }
        }
        val newDurationUs = updatedTracks.flatMap { it.clips }.maxOfOrNull { it.timelineEndUs } ?: 0L
        return project.copy(
            tracks = updatedTracks,
            durationUs = newDurationUs,
            modifiedAt = System.currentTimeMillis()
        )
    }

    /**
     * Adds a new clip to a track in the project and recalculates duration.
     */
    fun addClipToProject(
        project: ProjectManifest,
        trackId: String,
        clip: TimelineClip
    ): ProjectManifest {
        val updatedTracks = project.tracks.map { track ->
            if (track.id == trackId) {
                val newClips = (track.clips + clip).sortedBy { it.timelineStartUs }
                track.copy(clips = newClips)
            } else {
                track
            }
        }
        val newDurationUs = updatedTracks.flatMap { it.clips }.maxOfOrNull { it.timelineEndUs } ?: 0L
        return project.copy(
            tracks = updatedTracks,
            durationUs = newDurationUs,
            modifiedAt = System.currentTimeMillis()
        )
    }

    /**
     * Removes a clip from the project by ID and recalculates duration.
     */
    fun removeClipFromProject(
        project: ProjectManifest,
        clipId: String
    ): ProjectManifest {
        val updatedTracks = project.tracks.map { track ->
            val newClips = track.clips.filter { it.id != clipId }
            track.copy(clips = newClips)
        }
        val newDurationUs = updatedTracks.flatMap { it.clips }.maxOfOrNull { it.timelineEndUs } ?: 0L
        return project.copy(
            tracks = updatedTracks,
            durationUs = newDurationUs,
            modifiedAt = System.currentTimeMillis()
        )
    }
}
