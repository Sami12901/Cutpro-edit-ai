package com.cutpro.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ProjectManifest(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val modifiedAt: Long = System.currentTimeMillis(),
    val aspectRatio: CanvasAspectRatio = CanvasAspectRatio.RATIO_16_9,
    val defaultResolution: ExportResolution = ExportResolution.RES_1080P,
    val defaultFrameRate: ExportFrameRate = ExportFrameRate.FPS_30,
    val durationUs: Long = 0L,
    val tracks: List<TimelineTrack> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "Project id cannot be blank" }
        require(title.isNotBlank()) { "Project title cannot be blank" }
        require(durationUs >= 0L) { "durationUs ($durationUs) cannot be negative" }
    }

    fun calculateDerivedDurationUs(): Long {
        return tracks.maxOfOrNull { it.durationUs } ?: 0L
    }

    fun findTrackById(trackId: String): TimelineTrack? {
        return tracks.firstOrNull { it.id == trackId }
    }

    fun findClipById(clipId: String): TimelineClip? {
        return tracks.asSequence().flatMap { it.clips }.firstOrNull { it.id == clipId }
    }

    fun getAllClips(): List<TimelineClip> = tracks.flatMap { it.clips }

    fun getAllMediaUris(): Set<String> = getAllClips().map { it.mediaSource.uriString }.toSet()

    fun withUpdatedTrack(track: TimelineTrack): ProjectManifest {
        val updatedTracks = tracks.map { if (it.id == track.id) track else it }
        val newDurationUs = updatedTracks.maxOfOrNull { it.durationUs } ?: 0L
        return copy(
            tracks = updatedTracks,
            durationUs = newDurationUs,
            modifiedAt = System.currentTimeMillis()
        )
    }
}
