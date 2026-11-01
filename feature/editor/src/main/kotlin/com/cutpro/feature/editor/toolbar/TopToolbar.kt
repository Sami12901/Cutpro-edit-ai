package com.cutpro.feature.editor.toolbar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cutpro.core.designsystem.component.CutProButton
import com.cutpro.core.designsystem.component.CutProIconButton
import com.cutpro.core.designsystem.component.CutProOutlinedButton
import com.cutpro.core.designsystem.icon.CutProIcons
import com.cutpro.core.model.CanvasAspectRatio

/**
 * Top action toolbar providing Back, Title Editor (max 100 chars),
 * Reactive Undo / Redo buttons, Aspect Ratio quick toggle, and Export action.
 * Zero emojis permitted anywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopToolbar(
    title: String,
    canUndo: Boolean,
    canRedo: Boolean,
    aspectRatio: CanvasAspectRatio,
    onBackClick: () -> Unit,
    onUndoClick: () -> Unit,
    onRedoClick: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onAspectRatioChanged: (CanvasAspectRatio) -> Unit,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isEditingTitle by remember { mutableStateOf(false) }
    var titleInput by remember(title) { mutableStateOf(title) }
    var isRatioDropdownOpen by remember { mutableStateOf(false) }

    TopAppBar(
        modifier = modifier.fillMaxWidth(),
        title = {
            Box(
                modifier = Modifier
                    .clickable { isEditingTitle = true }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
            ) {
                Text(
                    text = title.ifBlank { "Untitled Project" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            CutProIconButton(
                icon = CutProIcons.Back,
                contentDescription = "Back to dashboard",
                onClick = onBackClick
            )
        },
        actions = {
            // Undo (reactive, disabled when stack is empty)
            CutProIconButton(
                icon = CutProIcons.Undo,
                contentDescription = "Undo",
                onClick = onUndoClick,
                enabled = canUndo
            )

            // Redo (reactive, disabled when stack is empty)
            CutProIconButton(
                icon = CutProIcons.Redo,
                contentDescription = "Redo",
                onClick = onRedoClick,
                enabled = canRedo
            )

            // Aspect Ratio Quick Toggle
            Box {
                CutProIconButton(
                    icon = CutProIcons.AspectRatio,
                    contentDescription = "Aspect Ratio",
                    onClick = { isRatioDropdownOpen = true }
                )

                DropdownMenu(
                    expanded = isRatioDropdownOpen,
                    onDismissRequest = { isRatioDropdownOpen = false }
                ) {
                    CanvasAspectRatio.entries.forEach { ratio ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = ratio.label,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            onClick = {
                                onAspectRatioChanged(ratio)
                                isRatioDropdownOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Export Action Button
            CutProButton(
                text = "Export",
                icon = CutProIcons.Export,
                onClick = onExportClick,
                modifier = Modifier.padding(end = 8.dp)
            )
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )

    // Project Title Editing Dialog
    if (isEditingTitle) {
        AlertDialog(
            onDismissRequest = { isEditingTitle = false },
            title = {
                Text(
                    text = "Edit Project Title",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                OutlinedTextField(
                    value = titleInput,
                    onValueChange = {
                        // Max 100 characters limit
                        if (it.length <= 100) {
                            titleInput = it
                        }
                    },
                    label = { Text("Title") },
                    supportingText = {
                        Text("${titleInput.length} / 100")
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                CutProButton(
                    text = "Save",
                    onClick = {
                        onTitleChanged(titleInput.trim())
                        isEditingTitle = false
                    }
                )
            },
            dismissButton = {
                CutProOutlinedButton(
                    text = "Cancel",
                    onClick = { isEditingTitle = false }
                )
            }
        )
    }
}
