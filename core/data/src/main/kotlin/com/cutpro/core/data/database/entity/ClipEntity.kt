package com.cutpro.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clips",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["track_id"], name = "index_clips_track_id"),
        Index(value = ["track_id", "timeline_start_us"], name = "index_clips_timeline_start")
    ]
)
data class ClipEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "source_uri")
    val sourceUri: String,

    @ColumnInfo(name = "file_name")
    val fileName: String,

    @ColumnInfo(name = "mime_type")
    val mimeType: String,

    @ColumnInfo(name = "timeline_start_us")
    val timelineStartUs: Long,

    @ColumnInfo(name = "timeline_duration_us")
    val timelineDurationUs: Long,

    @ColumnInfo(name = "source_in_us")
    val sourceInUs: Long,

    @ColumnInfo(name = "source_out_us")
    val sourceOutUs: Long,

    @ColumnInfo(name = "speed_multiplier")
    val speedMultiplier: Float = 1.0f,

    @ColumnInfo(name = "fit_mode")
    val fitMode: String = "FIT",

    @ColumnInfo(name = "transform_scale_x")
    val transformScaleX: Float = 1.0f,

    @ColumnInfo(name = "transform_scale_y")
    val transformScaleY: Float = 1.0f,

    @ColumnInfo(name = "transform_translation_x")
    val transformTranslationX: Float = 0.0f,

    @ColumnInfo(name = "transform_translation_y")
    val transformTranslationY: Float = 0.0f,

    @ColumnInfo(name = "transform_rotation_deg")
    val transformRotationDegrees: Float = 0.0f,

    @ColumnInfo(name = "transform_opacity")
    val transformOpacity: Float = 1.0f,

    @ColumnInfo(name = "volume")
    val volume: Float = 1.0f,

    @ColumnInfo(name = "is_audio_muted")
    val isAudioMuted: Boolean = false,

    @ColumnInfo(name = "fade_in_duration_us")
    val fadeInDurationUs: Long = 0L,

    @ColumnInfo(name = "fade_out_duration_us")
    val fadeOutDurationUs: Long = 0L,

    @ColumnInfo(name = "fade_in_curve")
    val fadeInCurve: String = "SCURVE",

    @ColumnInfo(name = "fade_out_curve")
    val fadeOutCurve: String = "SCURVE",

    @ColumnInfo(name = "custom_title")
    val customTitle: String? = null,

    @ColumnInfo(name = "media_width")
    val mediaWidth: Int = 0,

    @ColumnInfo(name = "media_height")
    val mediaHeight: Int = 0,

    @ColumnInfo(name = "media_rotation_deg")
    val mediaRotationDegrees: Int = 0,

    @ColumnInfo(name = "media_frame_rate")
    val mediaFrameRate: Float = 0.0f,

    @ColumnInfo(name = "media_audio_channels")
    val mediaAudioChannels: Int = 0,

    @ColumnInfo(name = "media_audio_sample_rate")
    val mediaAudioSampleRate: Int = 0
)
