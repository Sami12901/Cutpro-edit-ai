package com.cutpro.core.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaSourceInfo(
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
) {
    init {
        require(uriString.isNotBlank()) { "uriString cannot be blank" }
        require(fileName.isNotBlank()) { "fileName cannot be blank" }
        require(durationUs > 0L) { "durationUs ($durationUs) must be > 0" }
        require(width >= 0 && height >= 0) { "Dimensions cannot be negative" }
        require(audioChannels >= 0 && audioSampleRate >= 0) { "Audio parameters cannot be negative" }
        require(fileSizeBytes >= 0L) { "fileSizeBytes cannot be negative" }
    }

    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
    val isImage: Boolean get() = mimeType.startsWith("image/")
}
