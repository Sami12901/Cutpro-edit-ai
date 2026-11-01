package com.cutpro.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class VideoCodec(val mimeType: String, val displayName: String) {
    H264("video/avc", "H.264 / AVC"),
    H265("video/hevc", "H.265 / HEVC")
}

@Serializable
enum class TrackType {
    VIDEO,
    AUDIO,
    TEXT,
    OVERLAY,
    CAPTION;

    val isVisual: Boolean get() = this != AUDIO
    val isAudioOnly: Boolean get() = this == AUDIO
}

@Serializable
enum class ClipFitMode {
    FIT,       // Letterbox / Pillarbox maintaining aspect ratio
    FILL,      // Scale to cover canvas (crops excess)
    STRETCH,   // Stretch to fill canvas ignoring aspect ratio
    CUSTOM     // User defined scale and pan
}
