package com.cutpro.core.data.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracks",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"], name = "index_tracks_project_id"),
        Index(value = ["project_id", "track_index"], unique = true, name = "index_tracks_project_index")
    ]
)
data class TrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "project_id")
    val projectId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "track_type")
    val trackType: String,

    @ColumnInfo(name = "track_index")
    val trackIndex: Int,

    @ColumnInfo(name = "is_muted")
    val isMuted: Boolean = false,

    @ColumnInfo(name = "is_locked")
    val isLocked: Boolean = false,

    @ColumnInfo(name = "is_visible")
    val isVisible: Boolean = true,

    @ColumnInfo(name = "volume")
    val volume: Float = 1.0f
)
