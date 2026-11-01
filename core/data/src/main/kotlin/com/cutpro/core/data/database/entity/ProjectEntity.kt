package com.cutpro.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "projects",
    indices = [
        Index(value = ["updated_at"], name = "index_projects_updated_at")
    ]
)
data class ProjectEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,

    @ColumnInfo(name = "aspect_ratio")
    val aspectRatio: String,

    @ColumnInfo(name = "default_resolution")
    val defaultResolution: String,

    @ColumnInfo(name = "default_frame_rate")
    val defaultFrameRate: String,

    @ColumnInfo(name = "video_codec")
    val videoCodec: String,

    @ColumnInfo(name = "duration_us")
    val durationUs: Long,

    @ColumnInfo(name = "thumbnail_path")
    val thumbnailPath: String? = null,

    @ColumnInfo(name = "schema_version")
    val schemaVersion: Int = 1
)
