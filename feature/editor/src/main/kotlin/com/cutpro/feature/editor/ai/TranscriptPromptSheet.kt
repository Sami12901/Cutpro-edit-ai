package com.cutpro.feature.editor.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cutpro.core.ai.prompt.PromptObjectivePreset
import com.cutpro.core.ai.prompt.TranscriptSegment
import com.cutpro.core.ai.prompt.TranscriptToPromptEngine
import com.cutpro.core.model.ProjectManifest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptPromptSheet(
    project: ProjectManifest,
    segments: List<TranscriptSegment>,
    onDismiss: () -> Unit,
    onNavigateToCodeRunner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedPreset by remember { mutableStateOf(PromptObjectivePreset.VIRAL_SHORT_REEL) }
    var customInstructions by remember { mutableStateOf("") }
    var isDropdownExpanded by remember { mutableStateOf(false) }

    val generatedPrompt = remember(selectedPreset, customInstructions, project, segments) {
        TranscriptToPromptEngine.generatePrompt(
            project = project,
            segments = segments,
            preset = selectedPreset,
            customInstructions = customInstructions
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Generate AI Edit Prompt",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Extracts transcript & metadata into structured prompts for external AI models (ChatGPT, Claude, Gemini, DeepSeek).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Objective Preset Dropdown
            Text(
                text = "Editing Objective Preset",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                OutlinedButton(
                    onClick = { isDropdownExpanded = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedPreset.displayName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                DropdownMenu(
                    expanded = isDropdownExpanded,
                    onDismissRequest = { isDropdownExpanded = false }
                ) {
                    PromptObjectivePreset.entries.forEach { preset ->
                        DropdownMenuItem(
                            text = { Text(preset.displayName) },
                            onClick = {
                                selectedPreset = preset
                                isDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            // Custom Instructions input
            OutlinedTextField(
                value = customInstructions,
                onValueChange = { customInstructions = it },
                label = { Text("Custom Instructions (Optional)") },
                placeholder = { Text("E.g. Cut quiet parts, zoom on key statements, add Bengali subtitles") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                minLines = 2,
                maxLines = 4
            )

            // Generated Prompt Preview
            Text(
                text = "Generated Prompt Preview (${generatedPrompt.estimatedWordCount} words):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = generatedPrompt.fullPrompt,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("CutPro Prompt", generatedPrompt.fullPrompt)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Prompt copied to clipboard cleanly.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Copy Prompt")
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onNavigateToCodeRunner()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Open Code Runner")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
