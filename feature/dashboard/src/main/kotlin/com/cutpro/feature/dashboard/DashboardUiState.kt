package com.cutpro.feature.dashboard

import com.cutpro.core.model.ExportResolution

data class ProjectItemUiModel(
    val id: String,
    val title: String,
    val durationFormatted: String,
    val durationUs: Long,
    val aspectRatioLabel: String,
    val lastModifiedFormatted: String,
    val thumbnailUri: String? = null
)

data class TemplateItemUiModel(
    val id: String,
    val category: String,
    val title: String,
    val description: String
)

data class DashboardUiState(
    val isLoading: Boolean = false,
    val projects: List<ProjectItemUiModel> = emptyList(),
    val templates: List<TemplateItemUiModel> = emptyList(),
    val projectToDelete: ProjectItemUiModel? = null,
    val isSettingsOpen: Boolean = false,
    val isDarkTheme: Boolean = true,
    val defaultResolution: ExportResolution = ExportResolution.RES_1080P
)
