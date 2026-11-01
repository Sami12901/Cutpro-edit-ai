package com.cutpro.core.subtitles.model

enum class SubtitlePresetStyle(val displayName: String, val description: String) {
    HIGHLIGHT_PILL(
        "Rounded Pill Highlight",
        "Active spoken words highlighted inside a high-contrast rounded background pill."
    ),
    BOUNCING_POP(
        "Word-by-Word Pop Bounce",
        "Current spoken word scales and bounces up with spring animation."
    ),
    GLOW_OUTLINE(
        "Glowing Neon Outline",
        "Vibrant stroke glow around the active word with clean white text."
    ),
    TYPEWRITER(
        "Typewriter Terminal",
        "Characters reveal consecutively in synchronization with speech cadence."
    ),
    SHADOW_PUNCH(
        "3D Shadow Punch",
        "Deep 3D drop-shadow with dynamic punch-in scale on active phrase."
    ),
    LOWER_THIRD_DUO(
        "Cinematic Lower Third",
        "Modern two-tone news bar with clean horizontal alignment."
    );

    fun toCaptionStyle(): CaptionStyle {
        return when (this) {
            HIGHLIGHT_PILL -> CaptionStyle(
                fontSizeSp = 24f,
                textColorArgb = 0xFFFFFFFFL,
                strokeWidthPx = 0f,
                backgroundColorArgb = 0xEE1A1A24L,
                backgroundCornerRadiusPx = 14f,
                backgroundPaddingHorizontalPx = 20f,
                backgroundPaddingVerticalPx = 10f,
                activeWordColorArgb = 0xFFFFD700L,
                activeWordScaleFactor = 1.12f
            )
            BOUNCING_POP -> CaptionStyle(
                fontSizeSp = 26f,
                textColorArgb = 0xFFEEEEEEL,
                strokeColorArgb = 0xFF111111L,
                strokeWidthPx = 4f,
                activeWordColorArgb = 0xFFFF0055L,
                activeWordScaleFactor = 1.25f
            )
            GLOW_OUTLINE -> CaptionStyle(
                fontSizeSp = 24f,
                textColorArgb = 0xFFFFFFFFL,
                strokeColorArgb = 0xFF00E5FFL,
                strokeWidthPx = 6f,
                shadowColorArgb = 0xFF00E5FFL,
                shadowBlurPx = 12f,
                activeWordColorArgb = 0xFFFFFFFFL,
                activeWordScaleFactor = 1.15f
            )
            TYPEWRITER -> CaptionStyle(
                fontFamily = "monospace",
                fontSizeSp = 22f,
                textColorArgb = 0xFF00FF66L,
                backgroundColorArgb = 0xDD000000L,
                strokeWidthPx = 0f,
                activeWordColorArgb = 0xFFFFFFFFL
            )
            SHADOW_PUNCH -> CaptionStyle(
                fontSizeSp = 28f,
                textColorArgb = 0xFFFFCC00L,
                strokeColorArgb = 0xFF000000L,
                strokeWidthPx = 5f,
                shadowColorArgb = 0xFF000000L,
                shadowOffsetX = 6f,
                shadowOffsetY = 6f,
                shadowBlurPx = 8f,
                activeWordColorArgb = 0xFFFFFFFFL,
                activeWordScaleFactor = 1.2f
            )
            LOWER_THIRD_DUO -> CaptionStyle(
                fontSizeSp = 20f,
                textColorArgb = 0xFFFFFFFFL,
                backgroundColorArgb = 0xCC111111L,
                backgroundCornerRadiusPx = 4f,
                strokeWidthPx = 0f,
                activeWordColorArgb = 0xFF00E5FFL
            )
        }
    }
}

