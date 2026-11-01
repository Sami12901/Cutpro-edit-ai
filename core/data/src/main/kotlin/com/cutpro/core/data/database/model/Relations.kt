package com.cutpro.core.data.database.model

import androidx.room.Embedded
import androidx.room.Relation
import com.cutpro.core.data.database.entity.CaptionCueEntity
import com.cutpro.core.data.database.entity.ClipEntity
import com.cutpro.core.data.database.entity.KeyframeEntity
import com.cutpro.core.data.database.entity.ProjectEntity
import com.cutpro.core.data.database.entity.TrackEntity

data class ClipWithKeyframes(
    @Embedded
    val clip: ClipEntity,

    @Relation(
        parentColumn = "id",
        entityColumn = "clip_id"
    )
    val keyframes: List<KeyframeEntity> = emptyList()
)

data class TrackWithClips(
    @Embedded
    val track: TrackEntity,

    @Relation(
        entity = ClipEntity::class,
        parentColumn = "id",
        entityColumn = "track_id"
    )
    val clips: List<ClipWithKeyframes> = emptyList(),

    @Relation(
        parentColumn = "id",
        entityColumn = "track_id"
    )
    val captionCues: List<CaptionCueEntity> = emptyList()
)

data class ProjectWithTracks(
    @Embedded
    val project: ProjectEntity,

    @Relation(
        entity = TrackEntity::class,
        parentColumn = "id",
        entityColumn = "project_id"
    )
    val tracks: List<TrackWithClips> = emptyList()
)
