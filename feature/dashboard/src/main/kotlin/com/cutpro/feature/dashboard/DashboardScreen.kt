package com.cutpro.feature.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape


import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cutpro.core.designsystem.component.CutProButton
import com.cutpro.core.designsystem.component.CutProConfirmDialog
import com.cutpro.core.designsystem.component.CutProIconButton
import com.cutpro.core.designsystem.icon.CutProIcons
import com.cutpro.feature.dashboard.component.ProjectCard
import com.cutpro.feature.dashboard.component.SettingsDialog
import com.cutpro.feature.dashboard.component.TemplateGallerySection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToEditor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = CutProIcons.CropTool,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                        Text(
                            text = "CutPro",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    CutProButton(
                        text = "New Project",
                        icon = CutProIcons.Add,
                        onClick = {
                            viewModel.createNewProject { newProjectId ->
                                onNavigateToEditor(newProjectId)
                            }
                        },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    CutProIconButton(
                        icon = CutProIcons.Settings,
                        contentDescription = "Settings",
                        onClick = { viewModel.openSettings() }
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Templates Gallery Section
            item {
                Spacer(modifier = Modifier.height(8.dp))
                TemplateGallerySection(
                    templates = uiState.templates,
                    onSelectTemplate = { tpl ->
                        viewModel.createNewProject(title = tpl.title) { newProjectId ->
                            onNavigateToEditor(newProjectId)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Recent Projects Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Recent Projects",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${uiState.projects.size} projects",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Loading state
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (uiState.projects.isEmpty()) {
                // Empty state
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp, vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = CutProIcons.VideoTrack,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No recent projects found. Tap New Project to start editing.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            CutProButton(
                                text = "New Project",
                                icon = CutProIcons.Add,
                                onClick = {
                                    viewModel.createNewProject { newProjectId ->
                                        onNavigateToEditor(newProjectId)
                                    }
                                }
                            )
                        }
                    }
                }
            } else {
                // Recent Projects Grid
                items(uiState.projects.chunked(2)) { rowProjects ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (project in rowProjects) {
                            Box(modifier = Modifier.weight(1f)) {
                                ProjectCard(
                                    project = project,
                                    onClick = { onNavigateToEditor(project.id) },
                                    onDeleteClick = { viewModel.confirmDeleteProject(project) }
                                )
                            }
                        }
                        if (rowProjects.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (uiState.projectToDelete != null) {
        CutProConfirmDialog(
            title = "Delete Project",
            message = "Are you sure you want to delete this project? This action cannot be undone.",
            confirmText = "Delete",
            dismissText = "Cancel",
            onConfirm = { viewModel.executeDeleteProject() },
            onDismiss = { viewModel.dismissDeleteDialog() }
        )
    }

    // Settings Dialog
    if (uiState.isSettingsOpen) {
        SettingsDialog(
            isDarkTheme = uiState.isDarkTheme,
            onDarkThemeChanged = { viewModel.setDarkTheme(it) },
            defaultResolution = uiState.defaultResolution,
            onResolutionChanged = { viewModel.setDefaultResolution(it) },
            onDismiss = { viewModel.closeSettings() }
        )
    }
}
