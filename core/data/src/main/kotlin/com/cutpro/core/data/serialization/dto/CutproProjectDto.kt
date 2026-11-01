package com.cutpro.core.data.serialization.dto

import kotlinx.serialization.Serializable

@Serializable
data class CutproProjectDto(
    val version: Int = 1,
    val metadata: ProjectMetadataDto,
    val canvas: CanvasConfigDto,
    val tracks: List<TrackDto> = emptyList()
)

@Serializable
data class ProjectMetadataDto(
    val id: String,
    val title: String,
    val createdAt: Long,
    val modifiedAt: Long,
    val appVersion: String = "1.0.0"
)

@Serializable
data class CanvasConfigDto(
    val aspectRatio: String,
    val defaultResolution: String,
    val defaultFrameRate: String,
    val durationUs: Long
)

@Serializable
data class TrackDto(
    val id: String,
    val name: String,
    val trackType: String,
    val zIndex: Int,
    val isMuted: Boolean = false,
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val clips: List<ClipDto> = emptyList()
)

@Serializable
data class ClipDto(
    val id: String,
    val trackId: String,
    val mediaSource: MediaAssetDto,
    val timelineStartUs: Long,
    val timelineDurationUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val speedMultiplier: Float = 1.0f,
    val fitMode: String = "FIT",
    val transform: ClipTransformDto = ClipTransformDto(),
    val audioProperties: ClipAudioDto = ClipAudioDto(),
    val customTitle: String? = null
)

@Serializable
data class MediaAssetDto(
    val uriString: String,
    val fileName: String,
    val mimeType: String,
    val durationUs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val frameRate: Float = 0.0f,
    val audioChannels: Int = 0,
    val audioSampleRate: Int = 0,
    val fileSizeBytes: Long = 0L
)

@Serializable
data class ClipTransformDto(
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val translationX: Float = 0.0f,
    val translationY: Float = 0.0f,
    val rotationDegrees: Float = 0.0f,
    val opacity: Float = 1.0f
)

@Serializable
data class ClipAudioDto(
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val fadeInDurationUs: Long = 0L,
    val fadeOutDurationUs: Long = 0L
)
