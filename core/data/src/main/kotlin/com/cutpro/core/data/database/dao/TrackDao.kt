package com.cutpro.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cutpro.core.data.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Update
    suspend fun updateTrack(track: TrackEntity)

    @Query("DELETE FROM tracks WHERE id = :trackId")
    suspend fun deleteTrackById(trackId: String)

    @Query("DELETE FROM tracks WHERE project_id = :projectId")
    suspend fun deleteTracksForProject(projectId: String)

    @Query("SELECT * FROM tracks WHERE project_id = :projectId ORDER BY track_index ASC")
    fun observeTracksForProject(projectId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE project_id = :projectId ORDER BY track_index ASC")
    suspend fun getTracksForProject(projectId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    suspend fun getTrackById(trackId: String): TrackEntity?
}
