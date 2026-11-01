package com.cutpro.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cutpro.core.data.database.entity.KeyframeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyframeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyframe(keyframe: KeyframeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyframes(keyframes: List<KeyframeEntity>)

    @Update
    suspend fun updateKeyframe(keyframe: KeyframeEntity)

    @Query("DELETE FROM keyframes WHERE id = :keyframeId")
    suspend fun deleteKeyframeById(keyframeId: String)

    @Query("DELETE FROM keyframes WHERE clip_id = :clipId")
    suspend fun deleteKeyframesForClip(clipId: String)

    @Query("SELECT * FROM keyframes WHERE clip_id = :clipId ORDER BY timestamp_us ASC")
    fun observeKeyframesForClip(clipId: String): Flow<List<KeyframeEntity>>

    @Query("SELECT * FROM keyframes WHERE clip_id = :clipId ORDER BY timestamp_us ASC")
    suspend fun getKeyframesForClip(clipId: String): List<KeyframeEntity>

    @Query("SELECT * FROM keyframes WHERE clip_id = :clipId AND property_type = :propertyType ORDER BY timestamp_us ASC")
    suspend fun getKeyframesForProperty(clipId: String, propertyType: String): List<KeyframeEntity>
}
