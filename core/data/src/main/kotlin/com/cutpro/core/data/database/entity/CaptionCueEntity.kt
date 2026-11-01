package com.cutpro.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "caption_cues",
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
        Index(value = ["track_id"], name = "index_caption_cues_track_id"),
        Index(value = ["track_id", "start_time_us"], name = "index_caption_cues_time")
    ]
)
data class CaptionCueEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "start_time_us")
    val startTimeUs: Long,

    @ColumnInfo(name = "end_time_us")
    val endTimeUs: Long,

    @ColumnInfo(name = "text")
    val text: String,

    @ColumnInfo(name = "words_json")
    val wordsJson: String,

    @ColumnInfo(name = "style_json")
    val styleJson: String,

    @ColumnInfo(name = "layout_json")
    val layoutJson: String
)
