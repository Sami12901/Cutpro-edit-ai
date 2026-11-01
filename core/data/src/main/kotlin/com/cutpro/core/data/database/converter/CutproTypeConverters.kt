package com.cutpro.core.data.database.converter

import androidx.room.TypeConverter
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ClipFitMode
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.TrackType
import com.cutpro.core.model.VideoCodec

class CutproTypeConverters {

    @TypeConverter
    fun fromCanvasAspectRatio(value: CanvasAspectRatio): String = value.name

    @TypeConverter
    fun toCanvasAspectRatio(value: String): CanvasAspectRatio =
        runCatching { CanvasAspectRatio.valueOf(value) }.getOrDefault(CanvasAspectRatio.RATIO_16_9)

    @TypeConverter
    fun fromExportResolution(value: ExportResolution): String = value.name

    @TypeConverter
    fun toExportResolution(value: String): ExportResolution =
        runCatching { ExportResolution.valueOf(value) }.getOrDefault(ExportResolution.RES_1080P)

    @TypeConverter
    fun fromExportFrameRate(value: ExportFrameRate): String = value.name

    @TypeConverter
    fun toExportFrameRate(value: String): ExportFrameRate =
        runCatching { ExportFrameRate.valueOf(value) }.getOrDefault(ExportFrameRate.FPS_30)

    @TypeConverter
    fun fromVideoCodec(value: VideoCodec): String = value.name

    @TypeConverter
    fun toVideoCodec(value: String): VideoCodec =
        runCatching { VideoCodec.valueOf(value) }.getOrDefault(VideoCodec.H264)

    @TypeConverter
    fun fromTrackType(value: TrackType): String = value.name

    @TypeConverter
    fun toTrackType(value: String): TrackType =
        runCatching { TrackType.valueOf(value) }.getOrDefault(TrackType.VIDEO)

    @TypeConverter
    fun fromClipFitMode(value: ClipFitMode): String = value.name

    @TypeConverter
    fun toClipFitMode(value: String): ClipFitMode =
        runCatching { ClipFitMode.valueOf(value) }.getOrDefault(ClipFitMode.FIT)
}
