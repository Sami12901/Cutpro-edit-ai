package com.cutpro.core.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.cutpro.core.data.database.entity.ProjectEntity
import com.cutpro.core.data.database.model.ProjectWithTracks
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects ORDER BY updated_at DESC")
    fun observeAllProjects(): Flow<List<ProjectEntity>>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId")
    fun observeProjectWithTracks(projectId: String): Flow<ProjectWithTracks?>

    @Transaction
    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectWithTracks(projectId: String): ProjectWithTracks?
}
