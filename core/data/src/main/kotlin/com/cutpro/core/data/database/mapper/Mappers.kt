package com.cutpro.core.data.database.mapper

import com.cutpro.core.data.database.entity.ClipEntity
import com.cutpro.core.data.database.entity.ProjectEntity
import com.cutpro.core.data.database.entity.TrackEntity
import com.cutpro.core.data.database.model.ClipWithKeyframes
import com.cutpro.core.data.database.model.ProjectWithTracks
import com.cutpro.core.data.database.model.TrackWithClips
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ClipAudioProperties
import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ClipTransform
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.MediaSourceInfo
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineClip
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.core.model.VideoCodec

fun ProjectEntity.toDomain(tracks: List<TimelineTrack>): ProjectManifest = ProjectManifest(
    id = id,
    title = title,
    createdAt = createdAt,
    modifiedAt = updatedAt,
    aspectRatio = runCatching { CanvasAspectRatio.valueOf(aspectRatio) }.getOrDefault(CanvasAspectRatio.RATIO_16_9),
    defaultResolution = runCatching { ExportResolution.valueOf(defaultResolution) }.getOrDefault(ExportResolution.RES_1080P),
    defaultFrameRate = runCatching { ExportFrameRate.valueOf(defaultFrameRate) }.getOrDefault(ExportFrameRate.FPS_30),
    durationUs = durationUs,
    tracks = tracks
)

fun ProjectWithTracks.toDomain(): ProjectManifest =
    project.toDomain(tracks.map { it.toDomain() })

fun ProjectManifest.toEntity(
    videoCodec: VideoCodec = VideoCodec.H264,
    thumbnailPath: String? = null
): ProjectEntity = ProjectEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = modifiedAt,
    aspectRatio = aspectRatio.name,
    defaultResolution = defaultResolution.name,
    defaultFrameRate = defaultFrameRate.name,
    videoCodec = videoCodec.name,
    durationUs = durationUs,
    thumbnailPath = thumbnailPath
)

fun TrackWithClips.toDomain(): TimelineTrack = TimelineTrack(
    id = track.id,
    name = track.name,
    type = runCatching { TrackType.valueOf(track.trackType) }.getOrDefault(TrackType.VIDEO),
    zIndex = track.trackIndex,
    isMuted = track.isMuted,
    isLocked = track.isLocked,
    isVisible = track.isVisible,
    clips = clips.map { it.toDomain() }
)

fun TimelineTrack.toEntity(projectId: String): TrackEntity = TrackEntity(
    id = id,
    projectId = projectId,
    name = name,
    trackType = type.name,
    trackIndex = zIndex,
    isMuted = isMuted,
    isLocked = isLocked,
    isVisible = isVisible
)

fun ClipWithKeyframes.toDomain(): TimelineClip = TimelineClip(
    id = clip.id,
    trackId = clip.trackId,
    mediaSource = MediaSourceInfo(
        uriString = clip.sourceUri,
        fileName = clip.fileName,
        mimeType = clip.mimeType,
        durationUs = clip.timelineDurationUs.coerceAtLeast(clip.sourceOutUs),
        width = clip.mediaWidth,
        height = clip.mediaHeight,
        rotationDegrees = clip.mediaRotationDegrees,
        frameRate = clip.mediaFrameRate,
        audioChannels = clip.mediaAudioChannels,
        audioSampleRate = clip.mediaAudioSampleRate
    ),
    timelineStartUs = clip.timelineStartUs,
    timelineDurationUs = clip.timelineDurationUs,
    sourceInUs = clip.sourceInUs,
    sourceOutUs = clip.sourceOutUs,
    speedMultiplier = clip.speedMultiplier,
    fitMode = runCatching { ClipFitMode.valueOf(clip.fitMode) }.getOrDefault(ClipFitMode.FIT),
    transform = ClipTransform(
        scaleX = clip.transformScaleX,
        scaleY = clip.transformScaleY,
        translationX = clip.transformTranslationX,
        translationY = clip.transformTranslationY,
        rotationDegrees = clip.transformRotationDegrees,
        opacity = clip.transformOpacity
    ),
    audioProperties = ClipAudioProperties(
        volume = clip.volume,
        isMuted = clip.isAudioMuted,
        fadeInDurationUs = clip.fadeInDurationUs,
        fadeOutDurationUs = clip.fadeOutDurationUs
    ),
    customTitle = clip.customTitle
)

fun TimelineClip.toEntity(): ClipEntity = ClipEntity(
    id = id,
    trackId = trackId,
    sourceUri = mediaSource.uriString,
    fileName = mediaSource.fileName,
    mimeType = mediaSource.mimeType,
    timelineStartUs = timelineStartUs,
    timelineDurationUs = timelineDurationUs,
    sourceInUs = sourceInUs,
    sourceOutUs = sourceOutUs,
    speedMultiplier = speedMultiplier,
    fitMode = fitMode.name,
    transformScaleX = transform.scaleX,
    transformScaleY = transform.scaleY,
    transformTranslationX = transform.translationX,
    transformTranslationY = transform.translationY,
    transformRotationDegrees = transform.rotationDegrees,
    transformOpacity = transform.opacity,
    volume = audioProperties.volume,
    isAudioMuted = audioProperties.isMuted,
    fadeInDurationUs = audioProperties.fadeInDurationUs,
    fadeOutDurationUs = audioProperties.fadeOutDurationUs,
    customTitle = customTitle,
    mediaWidth = mediaSource.width,
    mediaHeight = mediaSource.height,
    mediaRotationDegrees = mediaSource.rotationDegrees,
    mediaFrameRate = mediaSource.frameRate,
    mediaAudioChannels = mediaSource.audioChannels,
    mediaAudioSampleRate = mediaSource.audioSampleRate
)
