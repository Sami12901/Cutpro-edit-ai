package com.cutpro.core.ai.model

import com.cutpro.core.model.ClipTransform
import java.util.UUID

sealed interface EditAction {
    val id: String
    val description: String
    val trackId: String
    val clipId: String?
    val startTimeUs: Long
    val endTimeUs: Long
}

data class CutSegmentAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String? = null,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val ripple: Boolean = true
) : EditAction {
    init {
        require(endTimeUs >= startTimeUs) { "endTimeUs must be >= startTimeUs" }
    }
}

data class DeleteSilenceAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String? = null,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val preRollSafetyUs: Long = 80_000L,
    val postRollSafetyUs: Long = 80_000L,
    val ripple: Boolean = true
) : EditAction {
    init {
        require(endTimeUs >= startTimeUs) { "endTimeUs must be >= startTimeUs" }
    }
}

data class AdjustSpeedAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val currentSpeedMultiplier: Float = 1.0f,
    val targetSpeedMultiplier: Float
) : EditAction {
    init {
        require(targetSpeedMultiplier > 0.0f) { "targetSpeedMultiplier must be > 0" }
    }
}

data class ApplyTemplateAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String? = null,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val templateId: String,
    val parameters: Map<String, String> = emptyMap()
) : EditAction

data class MuteNoiseAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String? = null,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val targetVolume: Float = 0.0f
) : EditAction

data class TrimClipAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val newSourceInUs: Long? = null,
    val newSourceOutUs: Long? = null
) : EditAction

data class PushInZoomAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val splitPointUs: Long,
    val zoomScale: Float = 1.15f
) : EditAction {
    init {
        require(zoomScale > 1.0f) { "zoomScale must be > 1.0" }
        require(splitPointUs in startTimeUs..endTimeUs) { "splitPointUs must be within action range" }
    }
}
