package com.cutpro.core.ai.model

import com.cutpro.core.model.BlendMode
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.ColorAdjustments
import com.cutpro.core.model.LutFilterType
import com.cutpro.core.model.SpeedCurve
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

data class AddOverlayAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String? = null,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val assetUriString: String,
    val scale: Float = 1.0f,
    val translationX: Float = 0.0f,
    val translationY: Float = 0.0f,
    val blendMode: BlendMode = BlendMode.NORMAL
) : EditAction {
    init {
        require(endTimeUs > startTimeUs) { "endTimeUs must be > startTimeUs" }
        require(assetUriString.isNotBlank()) { "assetUriString cannot be blank" }
        require(scale > 0.0f) { "scale must be > 0" }
    }
}

data class AddCaptionAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String? = null,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val text: String,
    val style: String = "HIGHLIGHT_PILL",
    val language: String = "en"
) : EditAction {
    init {
        require(endTimeUs > startTimeUs) { "endTimeUs must be > startTimeUs" }
        require(text.isNotBlank()) { "text cannot be blank" }
    }
}

data class ApplyColorGradeAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val adjustments: ColorAdjustments
) : EditAction

data class ApplyFilterAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val filterType: LutFilterType
) : EditAction

data class ApplySpeedCurveAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val curve: SpeedCurve
) : EditAction

data class DuckAudioAction(
    override val id: String = UUID.randomUUID().toString(),
    override val description: String,
    override val trackId: String,
    override val clipId: String? = null,
    override val startTimeUs: Long,
    override val endTimeUs: Long,
    val targetVolumeDb: Float = -18.0f
) : EditAction {
    init {
        require(endTimeUs > startTimeUs) { "endTimeUs must be > startTimeUs" }
    }
}

