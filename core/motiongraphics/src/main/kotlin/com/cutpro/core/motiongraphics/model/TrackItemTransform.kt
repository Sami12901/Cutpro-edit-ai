package com.cutpro.core.motiongraphics.model

data class TrackItemTransform(
    val positionX: Float = 0.0f,
    val positionY: Float = 0.0f,
    val scaleX: Float = 1.0f,
    val scaleY: Float = 1.0f,
    val rotationDegrees: Float = 0.0f,
    val opacity: Float = 1.0f,
    val anchorX: Float = 0.5f,
    val anchorY: Float = 0.5f
) {
    companion object {
        val IDENTITY = TrackItemTransform()
    }
}
