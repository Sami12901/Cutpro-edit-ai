package com.cutpro.core.data.serialization

import com.cutpro.core.data.serialization.dto.CanvasConfigDto
import com.cutpro.core.data.serialization.dto.ClipAudioDto
import com.cutpro.core.data.serialization.dto.ClipDto
import com.cutpro.core.data.serialization.dto.ClipTransformDto
import com.cutpro.core.data.serialization.dto.CutproProjectDto
import com.cutpro.core.data.serialization.dto.MediaAssetDto
import com.cutpro.core.data.serialization.dto.ProjectMetadataDto
import com.cutpro.core.data.serialization.dto.TrackDto
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
import kotlinx.serialization.json.Json

class UnsupportedSchemaVersionException(message: String) : IllegalArgumentException(message)

object ProjectJsonSerializer {

    private const val CURRENT_SCHEMA_VERSION = 1

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
    }

    fun serialize(manifest: ProjectManifest, appVersion: String = "1.0.0"): String {
        val dto = CutproProjectDto(
            version = CURRENT_SCHEMA_VERSION,
            metadata = ProjectMetadataDto(
                id = manifest.id,
                title = manifest.title,
                createdAt = manifest.createdAt,
                modifiedAt = manifest.modifiedAt,
                appVersion = appVersion
            ),
            canvas = CanvasConfigDto(
                aspectRatio = manifest.aspectRatio.name,
                defaultResolution = manifest.defaultResolution.name,
                defaultFrameRate = manifest.defaultFrameRate.name,
                durationUs = manifest.durationUs
            ),
            tracks = manifest.tracks.map { track ->
                TrackDto(
                    id = track.id,
                    name = track.name,
                    trackType = track.type.name,
                    zIndex = track.zIndex,
                    isMuted = track.isMuted,
                    isLocked = track.isLocked,
                    isVisible = track.isVisible,
                    clips = track.clips.map { clip ->
                        ClipDto(
                            id = clip.id,
                            trackId = clip.trackId,
                            mediaSource = MediaAssetDto(
                                uriString = clip.mediaSource.uriString,
                                fileName = clip.mediaSource.fileName,
                                mimeType = clip.mediaSource.mimeType,
                                durationUs = clip.mediaSource.durationUs,
                                width = clip.mediaSource.width,
                                height = clip.mediaSource.height,
                                rotationDegrees = clip.mediaSource.rotationDegrees,
                                frameRate = clip.mediaSource.frameRate,
                                audioChannels = clip.mediaSource.audioChannels,
                                audioSampleRate = clip.mediaSource.audioSampleRate,
                                fileSizeBytes = clip.mediaSource.fileSizeBytes
                            ),
                            timelineStartUs = clip.timelineStartUs,
                            timelineDurationUs = clip.timelineDurationUs,
                            sourceInUs = clip.sourceInUs,
                            sourceOutUs = clip.sourceOutUs,
                            speedMultiplier = clip.speedMultiplier,
                            fitMode = clip.fitMode.name,
                            transform = ClipTransformDto(
                                scaleX = clip.transform.scaleX,
                                scaleY = clip.transform.scaleY,
                                translationX = clip.transform.translationX,
                                translationY = clip.transform.translationY,
                                rotationDegrees = clip.transform.rotationDegrees,
                                opacity = clip.transform.opacity
                            ),
                            audioProperties = ClipAudioDto(
                                volume = clip.audioProperties.volume,
                                isMuted = clip.audioProperties.isMuted,
                                fadeInDurationUs = clip.audioProperties.fadeInDurationUs,
                                fadeOutDurationUs = clip.audioProperties.fadeOutDurationUs
                            ),
                            customTitle = clip.customTitle
                        )
                    }
                )
            }
        )
        return json.encodeToString(CutproProjectDto.serializer(), dto)
    }

    fun deserialize(jsonString: String): ProjectManifest {
        val dto = json.decodeFromString(CutproProjectDto.serializer(), jsonString)

        if (dto.version > CURRENT_SCHEMA_VERSION) {
            throw UnsupportedSchemaVersionException(
                "Project schema version ${dto.version} is not supported by CutPro v1.0 (max supported: $CURRENT_SCHEMA_VERSION)"
            )
        }

        val tracks = dto.tracks.map { trackDto ->
            TimelineTrack(
                id = trackDto.id,
                name = trackDto.name,
                type = runCatching { TrackType.valueOf(trackDto.trackType) }.getOrDefault(TrackType.VIDEO),
                zIndex = trackDto.zIndex,
                isMuted = trackDto.isMuted,
                isLocked = trackDto.isLocked,
                isVisible = trackDto.isVisible,
                clips = trackDto.clips.map { clipDto ->
                    TimelineClip(
                        id = clipDto.id,
                        trackId = clipDto.trackId,
                        mediaSource = MediaSourceInfo(
                            uriString = clipDto.mediaSource.uriString,
                            fileName = clipDto.mediaSource.fileName,
                            mimeType = clipDto.mediaSource.mimeType,
                            durationUs = clipDto.mediaSource.durationUs,
                            width = clipDto.mediaSource.width,
                            height = clipDto.mediaSource.height,
                            rotationDegrees = clipDto.mediaSource.rotationDegrees,
                            frameRate = clipDto.mediaSource.frameRate,
                            audioChannels = clipDto.mediaSource.audioChannels,
                            audioSampleRate = clipDto.mediaSource.audioSampleRate,
                            fileSizeBytes = clipDto.mediaSource.fileSizeBytes
                        ),
                        timelineStartUs = clipDto.timelineStartUs,
                        timelineDurationUs = clipDto.timelineDurationUs,
                        sourceInUs = clipDto.sourceInUs,
                        sourceOutUs = clipDto.sourceOutUs,
                        speedMultiplier = clipDto.speedMultiplier,
                        fitMode = runCatching { ClipFitMode.valueOf(clipDto.fitMode) }.getOrDefault(ClipFitMode.FIT),
                        transform = ClipTransform(
                            scaleX = clipDto.transform.scaleX,
                            scaleY = clipDto.transform.scaleY,
                            translationX = clipDto.transform.translationX,
                            translationY = clipDto.transform.translationY,
                            rotationDegrees = clipDto.transform.rotationDegrees,
                            opacity = clipDto.transform.opacity
                        ),
                        audioProperties = ClipAudioProperties(
                            volume = clipDto.audioProperties.volume,
                            isMuted = clipDto.audioProperties.isMuted,
                            fadeInDurationUs = clipDto.audioProperties.fadeInDurationUs,
                            fadeOutDurationUs = clipDto.audioProperties.fadeOutDurationUs
                        ),
                        customTitle = clipDto.customTitle
                    )
                }
            )
        }

        return ProjectManifest(
            id = dto.metadata.id,
            title = dto.metadata.title,
            createdAt = dto.metadata.createdAt,
            modifiedAt = dto.metadata.modifiedAt,
            aspectRatio = runCatching { CanvasAspectRatio.valueOf(dto.canvas.aspectRatio) }.getOrDefault(CanvasAspectRatio.RATIO_16_9),
            defaultResolution = runCatching { ExportResolution.valueOf(dto.canvas.defaultResolution) }.getOrDefault(ExportResolution.RES_1080P),
            defaultFrameRate = runCatching { ExportFrameRate.valueOf(dto.canvas.defaultFrameRate) }.getOrDefault(ExportFrameRate.FPS_30),
            durationUs = dto.canvas.durationUs,
            tracks = tracks
        )
    }
}
