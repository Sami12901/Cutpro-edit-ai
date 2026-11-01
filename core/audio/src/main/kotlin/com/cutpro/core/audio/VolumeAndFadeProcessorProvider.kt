package com.cutpro.core.audio

import androidx.media3.common.audio.AudioProcessor

interface VolumeAndFadeProcessorProvider {
    fun createProcessor(
        clipStartUs: Long,
        clipDurationUs: Long,
        volume: Float,
        fadeInUs: Long,
        fadeInCurve: FadeCurveType,
        fadeOutUs: Long,
        fadeOutCurve: FadeCurveType
    ): AudioProcessor
}

class DefaultVolumeAndFadeProcessorProvider : VolumeAndFadeProcessorProvider {
    override fun createProcessor(
        clipStartUs: Long,
        clipDurationUs: Long,
        volume: Float,
        fadeInUs: Long,
        fadeInCurve: FadeCurveType,
        fadeOutUs: Long,
        fadeOutCurve: FadeCurveType
    ): AudioProcessor {
        return VolumeAndFadeAudioProcessor(
            clipDurationUs = clipDurationUs,
            initialVolume = volume,
            initialMuted = false,
            fadeInDurationUs = fadeInUs,
            fadeInCurve = fadeInCurve,
            fadeOutDurationUs = fadeOutUs,
            fadeOutCurve = fadeOutCurve
        ).apply {
            seekTo(clipStartUs)
        }
    }
}
