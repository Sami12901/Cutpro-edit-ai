package com.cutpro.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cutpro.core.data.database.dao.ProjectDao
import com.cutpro.core.data.database.entity.ProjectEntity
import com.cutpro.core.data.database.mapper.toDomain
import com.cutpro.core.data.database.mapper.toEntity
import com.cutpro.core.model.CanvasAspectRatio
import com.cutpro.core.model.ExportFrameRate
import com.cutpro.core.model.ExportResolution
import com.cutpro.core.model.ProjectManifest
import com.cutpro.core.model.TimelineTrack
import com.cutpro.core.model.TrackType
import com.cutpro.feature.dashboard.component.BuiltInTemplates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DashboardViewModel(
    private val projectDao: ProjectDao? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        DashboardUiState(
            templates = BuiltInTemplates
        )
    )
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadProjects()
    }

    fun loadProjects() {
        if (projectDao == null) {
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                projectDao.observeAllProjects().collect { entities ->
                    val uiProjects = entities.map { entity ->
                        val durationSec = entity.durationUs / 1_000_000L
                        val min = durationSec / 60
                        val sec = durationSec % 60
                        val formattedDuration = String.format(Locale.US, "%02d:%02d", min, sec)

                        val aspectRatio = runCatching {
                            CanvasAspectRatio.valueOf(entity.aspectRatio)
                        }.getOrDefault(CanvasAspectRatio.RATIO_16_9)

                        val lastEdited = formatTimestamp(entity.updatedAt)

                        ProjectItemUiModel(
                            id = entity.id,
                            title = entity.title,
                            durationFormatted = formattedDuration,
                            durationUs = entity.durationUs,
                            aspectRatioLabel = "${aspectRatio.widthRatio}:${aspectRatio.heightRatio}",
                            lastModifiedFormatted = lastEdited,
                            thumbnailUri = entity.thumbnailPath
                        )
                    }
                    _uiState.update { it.copy(projects = uiProjects, isLoading = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun createNewProject(
        title: String = "Untitled Project",
        aspectRatio: CanvasAspectRatio = CanvasAspectRatio.RATIO_16_9,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val projectId = UUID.randomUUID().toString()
            val videoTrack = TimelineTrack(
                id = UUID.randomUUID().toString(),
                name = "Video 1",
                type = TrackType.VIDEO,
                zIndex = 0
            )
            val audioTrack = TimelineTrack(
                id = UUID.randomUUID().toString(),
                name = "Audio 1",
                type = TrackType.AUDIO,
                zIndex = 1
            )
            val manifest = ProjectManifest(
                id = projectId,
                title = title,
                createdAt = System.currentTimeMillis(),
                modifiedAt = System.currentTimeMillis(),
                aspectRatio = aspectRatio,
                defaultResolution = _uiState.value.defaultResolution,
                defaultFrameRate = ExportFrameRate.FPS_30,
                durationUs = 0L,
                tracks = listOf(videoTrack, audioTrack)
            )

            if (projectDao != null) {
                projectDao.insertProject(manifest.toEntity())
            } else {
                // In-memory fallback
                val newProjectItem = ProjectItemUiModel(
                    id = manifest.id,
                    title = manifest.title,
                    durationFormatted = "00:00",
                    durationUs = 0L,
                    aspectRatioLabel = "${aspectRatio.widthRatio}:${aspectRatio.heightRatio}",
                    lastModifiedFormatted = "Today"
                )
                _uiState.update { state ->
                    state.copy(projects = listOf(newProjectItem) + state.projects)
                }
            }
            onCreated(projectId)
        }
    }

    fun confirmDeleteProject(project: ProjectItemUiModel) {
        _uiState.update { it.copy(projectToDelete = project) }
    }

    fun dismissDeleteDialog() {
        _uiState.update { it.copy(projectToDelete = null) }
    }

    fun executeDeleteProject() {
        val toDelete = _uiState.value.projectToDelete ?: return
        viewModelScope.launch {
            if (projectDao != null) {
                projectDao.deleteProjectById(toDelete.id)
            } else {
                _uiState.update { state ->
                    state.copy(projects = state.projects.filter { it.id != toDelete.id })
                }
            }
            _uiState.update { it.copy(projectToDelete = null) }
        }
    }

    fun openSettings() {
        _uiState.update { it.copy(isSettingsOpen = true) }
    }

    fun closeSettings() {
        _uiState.update { it.copy(isSettingsOpen = false) }
    }

    fun setDarkTheme(isDark: Boolean) {
        _uiState.update { it.copy(isDarkTheme = isDark) }
    }

    fun setDefaultResolution(resolution: ExportResolution) {
        _uiState.update { it.copy(defaultResolution = resolution) }
    }

    private fun formatTimestamp(timestampMs: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestampMs
        return when {
            diffMs < 24 * 60 * 60 * 1000L -> "Today"
            diffMs < 48 * 60 * 60 * 1000L -> "Yesterday"
            else -> SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date(timestampMs))
        }
    }
}
