package com.cutpro.core.subtitles.model

import java.util.UUID

data class CaptionTrack(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Captions",
    val languageCode: String = "en",
    val isVisible: Boolean = true,
    val cues: List<CaptionCue> = emptyList(),
    val defaultStyle: CaptionStyle = CaptionStyle.DEFAULT
)

data class CaptionCue(
    val startTimeUs: Long,
    val endTimeUs: Long,
    val text: String,
    val words: List<TimedWord> = emptyList(),
    val customStyle: CaptionStyle? = null,
    val layout: CaptionLayout = CaptionLayout.DEFAULT,
    val id: String = UUID.randomUUID().toString()
) {
    /**
     * Secondary convenience constructor supporting explicit `id` as the first argument,
     * maintaining backward compatibility with any legacy call site.
     */
    constructor(
        id: String,
        startTimeUs: Long,
        endTimeUs: Long,
        text: String,
        words: List<TimedWord> = emptyList(),
        customStyle: CaptionStyle? = null,
        layout: CaptionLayout = CaptionLayout.DEFAULT
    ) : this(
        startTimeUs,
        endTimeUs,
        text,
        words,
        customStyle,
        layout,
        id
    )

    init {
        require(endTimeUs >= startTimeUs) {
            "Cue endTimeUs ($endTimeUs) must be >= startTimeUs ($startTimeUs)"
        }
    }
}

data class TimedWord(
    val word: String,
    val startOffsetUs: Long,
    val endOffsetUs: Long
)

data class CaptionStyle(
    val fontFamily: String = "Roboto",
    val fontSizeSp: Float = 22f,
    val fontWeight: Int = 700,
    val textColorArgb: Long = 0xFFFFFFFFL,
    val strokeColorArgb: Long = 0xFF000000L,
    val strokeWidthPx: Float = 4.0f,
    val shadowColorArgb: Long = 0x80000000L,
    val shadowBlurPx: Float = 6.0f,
    val shadowOffsetX: Float = 2.0f,
    val shadowOffsetY: Float = 2.0f,
    val backgroundColorArgb: Long = 0x00000000L,
    val backgroundCornerRadiusPx: Float = 8.0f,
    val backgroundPaddingHorizontalPx: Float = 16.0f,
    val backgroundPaddingVerticalPx: Float = 8.0f,
    val activeWordColorArgb: Long = 0xFFFFD700L,
    val activeWordScaleFactor: Float = 1.15f
) {
    companion object {
        val DEFAULT = CaptionStyle()
        val HIGHLIGHT_BOX = CaptionStyle(
            textColorArgb = 0xFFFFFFFFL,
            backgroundColorArgb = 0xCC111111L,
            strokeWidthPx = 0f
        )
        val KARAOKE_POP = CaptionStyle(
            textColorArgb = 0xFFE0E0E0L,
            activeWordColorArgb = 0xFFFF3366L,
            activeWordScaleFactor = 1.25f
        )
    }
}

data class CaptionLayout(
    val verticalAlignment: VerticalAlignment = VerticalAlignment.BOTTOM,
    val horizontalAlignment: HorizontalAlignment = HorizontalAlignment.CENTER,
    val marginHorizontalPercent: Float = 0.05f,
    val marginVerticalPercent: Float = 0.08f,
    val maxLines: Int = 3
) {
    companion object {
        val DEFAULT = CaptionLayout()
    }
}

enum class VerticalAlignment {
    TOP,
    CENTER,
    BOTTOM
}

enum class HorizontalAlignment {
    START,
    CENTER,
    END
}
