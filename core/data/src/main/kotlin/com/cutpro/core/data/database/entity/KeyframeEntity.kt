package com.cutpro.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "keyframes",
    foreignKeys = [
        ForeignKey(
            entity = ClipEntity::class,
            parentColumns = ["id"],
            childColumns = ["clip_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["clip_id"], name = "index_keyframes_clip_id"),
        Index(value = ["clip_id", "property_type", "timestamp_us"], name = "index_keyframes_property_time")
    ]
)
data class KeyframeEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "clip_id")
    val clipId: String,

    @ColumnInfo(name = "property_type")
    val propertyType: String,

    @ColumnInfo(name = "timestamp_us")
    val timestampUs: Long,

    @ColumnInfo(name = "value_json")
    val valueJson: String,

    @ColumnInfo(name = "easing_type")
    val easingType: String,

    @ColumnInfo(name = "easing_params_json")
    val easingParamsJson: String? = null
)
