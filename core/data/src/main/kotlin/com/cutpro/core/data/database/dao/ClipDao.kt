package com.cutpro.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cutpro.core.data.database.entity.ClipEntity
import com.cutpro.core.data.database.model.ClipWithKeyframes
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: ClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Update
    suspend fun updateClip(clip: ClipEntity)

    @Query("DELETE FROM clips WHERE id = :clipId")
    suspend fun deleteClipById(clipId: String)

    @Query("DELETE FROM clips WHERE track_id = :trackId")
    suspend fun deleteClipsForTrack(trackId: String)

    @Query("SELECT * FROM clips WHERE track_id = :trackId ORDER BY timeline_start_us ASC")
    fun observeClipsForTrack(trackId: String): Flow<List<ClipEntity>>

    @Query("SELECT * FROM clips WHERE track_id = :trackId ORDER BY timeline_start_us ASC")
    suspend fun getClipsForTrack(trackId: String): List<ClipEntity>

    @Query("SELECT * FROM clips WHERE id = :clipId")
    suspend fun getClipById(clipId: String): ClipEntity?

    @Transaction
    @Query("SELECT * FROM clips WHERE id = :clipId")
    fun observeClipWithKeyframes(clipId: String): Flow<ClipWithKeyframes?>

    @Transaction
    @Query("SELECT * FROM clips WHERE id = :clipId")
    suspend fun getClipWithKeyframes(clipId: String): ClipWithKeyframes?

    @Query("UPDATE clips SET timeline_start_us = :startUs, timeline_duration_us = :durationUs, source_in_us = :inUs, source_out_us = :outUs WHERE id = :clipId")
    suspend fun updateClipTimelineBounds(clipId: String, startUs: Long, durationUs: Long, inUs: Long, outUs: Long)
}
