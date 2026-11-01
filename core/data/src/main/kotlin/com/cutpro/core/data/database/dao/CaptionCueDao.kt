package com.cutpro.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cutpro.core.data.database.entity.CaptionCueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptionCueDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCue(cue: CaptionCueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCues(cues: List<CaptionCueEntity>)

    @Update
    suspend fun updateCue(cue: CaptionCueEntity)

    @Query("DELETE FROM caption_cues WHERE id = :cueId")
    suspend fun deleteCueById(cueId: String)

    @Query("DELETE FROM caption_cues WHERE track_id = :trackId")
    suspend fun deleteCuesForTrack(trackId: String)

    @Query("SELECT * FROM caption_cues WHERE track_id = :trackId ORDER BY start_time_us ASC")
    fun observeCuesForTrack(trackId: String): Flow<List<CaptionCueEntity>>

    @Query("SELECT * FROM caption_cues WHERE track_id = :trackId ORDER BY start_time_us ASC")
    suspend fun getCuesForTrack(trackId: String): List<CaptionCueEntity>
}
