package com.cutpro.core.export.composition

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.cutpro.core.audio.FadeCurveType
import com.cutpro.core.audio.StereoPanAudioProcessor
import com.cutpro.core.audio.VolumeAndFadeAudioProcessor
import com.cutpro.core.export.model.ExportPlan
import com.cutpro.core.export.overlay.CompositionCanvasOverlay
import com.cutpro.core.export.overlay.MotionGraphicsTimelineItem
import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.RenderPlan
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.subtitles.model.CaptionCue
import com.cutpro.core.subtitles.model.CaptionStyle

/**
 * Helper sequence builder for constructing EditedMediaItemSequence instances with timeline gaps.
 */
@OptIn(UnstableApi::class)
class SequenceBuilder(
    private val isLooping: Boolean = false
) {
    private val items = mutableListOf<EditedMediaItem>()

    fun addItem(item: EditedMediaItem): SequenceBuilder {
        items.add(item)
        return this
    }

    fun addGap(gapUs: Long): SequenceBuilder {
        if (gapUs > 0L) {
            val mediaItem = MediaItem.Builder()
                .setMediaId("gap_${System.nanoTime()}")
                .setUri(Uri.EMPTY)
                .build()
            val gapItem = EditedMediaItem.Builder(mediaItem)
                .setDurationUs(gapUs)
                .setRemoveAudio(true)
                .setRemoveVideo(false)
                .build()
            items.add(gapItem)
        }
        return this
    }

    fun build(): EditedMediaItemSequence {
        return EditedMediaItemSequence(items, isLooping)
    }
}

/**
 * Media3 Composition Builder assembling multitrack audio, video, speed scaling,
 * transform effects, and FIFO CanvasOverlay graphics.
 * Strictly zero emojis.
 */
@OptIn(UnstableApi::class)
class Media3CompositionBuilder(
    private val context: Context? = null
) {

    fun buildComposition(
        plan: ExportPlan,
        motionItems: List<MotionGraphicsTimelineItem> = emptyList(),
        captionCues: List<CaptionCue> = emptyList(),
        captionStyle: CaptionStyle = CaptionStyle.DEFAULT
    ): Composition {
        return buildComposition(
            project = plan.project,
            renderPlan = plan.toRenderPlan(),
            includeMotionGraphics = plan.includeMotionGraphics,
            includeSubtitles = plan.includeSubtitles,
            motionItems = motionItems,
            captionCues = captionCues,
            captionStyle = captionStyle
        )
    }

    fun buildComposition(
        project: ProjectManifest,
        renderPlan: RenderPlan,
        includeMotionGraphics: Boolean = true,
        includeSubtitles: Boolean = true,
        motionItems: List<MotionGraphicsTimelineItem> = emptyList(),
        captionCues: List<CaptionCue> = emptyList(),
        captionStyle: CaptionStyle = CaptionStyle.DEFAULT
    ): Composition {
        val sequences = mutableListOf<EditedMediaItemSequence>()
        val targetHeight = renderPlan.resolution.height
        val (outputWidth, outputHeight) = renderPlan.aspectRatio.calculateMacroblockDimensions(targetHeight, 16)

        // 1. Primary Visual Track (TrackType.VIDEO)
        val videoTrack = project.tracks.firstOrNull { it.type == TrackType.VIDEO && it.isVisible }
        if (videoTrack != null && videoTrack.clips.isNotEmpty()) {
            val videoSequence = buildVideoSequence(
                track = videoTrack,
                projectDurationUs = project.durationUs,
                outputWidth = outputWidth,
                outputHeight = outputHeight
            )
            sequences.add(videoSequence)
        } else if (project.durationUs > 0L && (includeMotionGraphics || includeSubtitles)) {
            // When no source video clip exists, provide a black background gap for overlay rendering
            val sequenceBuilder = SequenceBuilder()
            sequenceBuilder.addGap(project.durationUs)
            sequences.add(sequenceBuilder.build())
        }

        // 2. Dedicated Audio Tracks (TrackType.AUDIO)
        val audioTracks = project.tracks.filter { it.type == TrackType.AUDIO && !it.isMuted }
        for (audioTrack in audioTracks) {
            if (audioTrack.clips.isNotEmpty()) {
                val audioSequence = buildAudioSequence(
                    track = audioTrack,
                    projectDurationUs = project.durationUs
                )
                sequences.add(audioSequence)
            }
        }

        // 3. Composition-level Video Effects (Framing & FIFO Overlays)
        val compositionVideoEffects = mutableListOf<Effect>()

        // Target output aspect ratio presentation framing
        compositionVideoEffects.add(
            Presentation.createForWidthAndHeight(
                outputWidth,
                outputHeight,
                Presentation.LAYOUT_SCALE_TO_FIT
            )
        )

        // Overlay effect bundling MotionGraphics (first) and Subtitles (second)
        val effectiveMotionItems = if (includeMotionGraphics) motionItems else emptyList()
        val effectiveCaptionCues = if (includeSubtitles) captionCues else emptyList()

        val canvasBundle = CompositionCanvasOverlay.create(
            width = outputWidth,
            height = outputHeight,
            motionItems = effectiveMotionItems,
            captionCues = effectiveCaptionCues,
            captionStyle = captionStyle
        )

        canvasBundle.toOverlayEffect()?.let { overlayEffect ->
            compositionVideoEffects.add(overlayEffect)
        }

        return Composition.Builder(sequences)
            .setEffects(Effects(/* audioProcessors = */ emptyList(), /* videoEffects = */ compositionVideoEffects))
            .setHdrMode(Composition.HDR_MODE_KEEP_HDR)
            .build()
    }

    private fun buildVideoSequence(
        track: TimelineTrack,
        projectDurationUs: Long,
        outputWidth: Int,
        outputHeight: Int
    ): EditedMediaItemSequence {
        val sequenceBuilder = SequenceBuilder()
        var currentPositionUs = 0L

        val sortedClips = track.clips.sortedBy { it.timelineStartUs }
        for (clip in sortedClips) {
            val gapUs = clip.timelineStartUs - currentPositionUs
            if (gapUs > 0L) {
                sequenceBuilder.addGap(gapUs)
            }

            val editedItem = buildEditedVideoItem(
                clip = clip,
                trackMuted = track.isMuted,
                outputWidth = outputWidth,
                outputHeight = outputHeight
            )
            sequenceBuilder.addItem(editedItem)
            currentPositionUs = clip.timelineEndUs
        }

        if (projectDurationUs > currentPositionUs) {
            sequenceBuilder.addGap(projectDurationUs - currentPositionUs)
        }

        return sequenceBuilder.build()
    }

    private fun buildEditedVideoItem(
        clip: TimelineClip,
        trackMuted: Boolean,
        outputWidth: Int,
        outputHeight: Int
    ): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(clip.mediaSource.uriString)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionUs(clip.sourceInUs)
                    .setEndPositionUs(clip.sourceOutUs)
                    .setStartsAtKeyFrame(false)
                    .build()
            )
            .build()

        val audioProcessors = mutableListOf<AudioProcessor>()
        val videoEffects = mutableListOf<Effect>()

        // Video FitMode & Presentation
        val presentationLayout = when (clip.fitMode) {
            ClipFitMode.FIT -> Presentation.LAYOUT_SCALE_TO_FIT
            ClipFitMode.FILL -> Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            ClipFitMode.STRETCH -> Presentation.LAYOUT_STRETCH_TO_FIT
            ClipFitMode.CUSTOM -> Presentation.LAYOUT_SCALE_TO_FIT
        }
        videoEffects.add(
            Presentation.createForWidthAndHeight(
                outputWidth,
                outputHeight,
                presentationLayout
            )
        )

        // ClipTransform (scale, rotate, opacity)
        if (!clip.transform.isIdentity) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(clip.transform.scaleX, clip.transform.scaleY)
                    .setRotationDegrees(clip.transform.rotationDegrees)
                    .build()
            )
            if (clip.transform.opacity < 1.0f) {
                videoEffects.add(AlphaScale(clip.transform.opacity))
            }
        }

        // Video Speed
        if (clip.speedMultiplier != 1.0f) {
            videoEffects.add(SpeedChangeEffect(clip.speedMultiplier))
        }

        // Audio Handling
        val shouldStripAudio = trackMuted || clip.audioProperties.isMuted || clip.mediaSource.audioChannels == 0
        if (!shouldStripAudio) {
            val (fadeInUs, fadeOutUs) = clip.audioProperties.calculateEffectiveFades(clip.timelineDurationUs)
            audioProcessors.add(
                VolumeAndFadeAudioProcessor(
                    clipDurationUs = clip.timelineDurationUs,
                    initialVolume = clip.audioProperties.volume,
                    initialMuted = false,
                    fadeInDurationUs = fadeInUs,
                    fadeInCurve = FadeCurveType.LINEAR,
                    fadeOutDurationUs = fadeOutUs,
                    fadeOutCurve = FadeCurveType.LINEAR
                )
            )

            if (clip.audioProperties.pan != 0.0f) {
                audioProcessors.add(StereoPanAudioProcessor(clip.audioProperties.pan))
            }

            if (clip.speedMultiplier != 1.0f) {
                audioProcessors.add(
                    SonicAudioProcessor().apply {
                        setSpeed(clip.speedMultiplier)
                        setPitch(1.0f)
                    }
                )
            }
        }

        return EditedMediaItem.Builder(mediaItem)
            .setRemoveAudio(shouldStripAudio)
            .setEffects(Effects(audioProcessors, videoEffects))
            .build()
    }

    private fun buildAudioSequence(
        track: TimelineTrack,
        projectDurationUs: Long
    ): EditedMediaItemSequence {
        val sequenceBuilder = SequenceBuilder()
        var currentPositionUs = 0L

        val sortedClips = track.clips.sortedBy { it.timelineStartUs }
        for (clip in sortedClips) {
            val gapUs = clip.timelineStartUs - currentPositionUs
            if (gapUs > 0L) {
                sequenceBuilder.addGap(gapUs)
            }

            val mediaItem = MediaItem.Builder()
                .setUri(clip.mediaSource.uriString)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(clip.sourceInUs)
                        .setEndPositionUs(clip.sourceOutUs)
                        .setStartsAtKeyFrame(false)
                        .build()
                )
                .build()

            val audioProcessors = mutableListOf<AudioProcessor>()
            val (fadeInUs, fadeOutUs) = clip.audioProperties.calculateEffectiveFades(clip.timelineDurationUs)

            audioProcessors.add(
                VolumeAndFadeAudioProcessor(
                    clipDurationUs = clip.timelineDurationUs,
                    initialVolume = if (clip.audioProperties.isMuted) 0f else clip.audioProperties.volume,
                    initialMuted = clip.audioProperties.isMuted,
                    fadeInDurationUs = fadeInUs,
                    fadeInCurve = FadeCurveType.LINEAR,
                    fadeOutDurationUs = fadeOutUs,
                    fadeOutCurve = FadeCurveType.LINEAR
                )
            )

            if (clip.audioProperties.pan != 0.0f) {
                audioProcessors.add(StereoPanAudioProcessor(clip.audioProperties.pan))
            }

            if (clip.speedMultiplier != 1.0f) {
                audioProcessors.add(
                    SonicAudioProcessor().apply {
                        setSpeed(clip.speedMultiplier)
                        setPitch(1.0f)
                    }
                )
            }

            val editedItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .setEffects(Effects(audioProcessors, emptyList()))
                .build()

            sequenceBuilder.addItem(editedItem)
            currentPositionUs = clip.timelineEndUs
        }

        if (projectDurationUs > currentPositionUs) {
            sequenceBuilder.addGap(projectDurationUs - currentPositionUs)
        }

        return sequenceBuilder.build()
    }
}
