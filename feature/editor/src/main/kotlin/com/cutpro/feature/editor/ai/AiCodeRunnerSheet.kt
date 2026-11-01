package com.cutpro.feature.editor.ai

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import com.cutpro.core.ai.script.CutProScriptParser
import com.cutpro.core.ai.script.ScriptParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiCodeRunnerSheet(
    onDismiss: () -> Unit,
    onExecuteScript: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var scriptText by remember {
        mutableStateOf(
            """
            // CutPro v2 Auto Edit Script
            SPLIT(track="main", time="00:03.500")
            DELETE_RANGE(track="main", start="00:03.500", end="00:05.100", ripple=true)
            APPLY_ZOOM_PUNCH(time="00:07.500", factor=1.15)
            ADD_CAPTION(text="স্বাগতম কাটপ্রো এআই স্টুডিওতে!", start="00:00.000", end="00:03.500", style="HIGHLIGHT_PILL", lang="bn")
            APPLY_SPEED_RAMP(curve="HERO")
            DUCK_AUDIO(start="00:00.000", end="00:05.000", db=-18)
            """.trimIndent()
        )
    }

    val validationResult = remember(scriptText) {
        try {
            val ast = CutProScriptParser.parse(scriptText)
            Pair(true, "Syntax Clean (${ast.statements.size} actions ready)")
        } catch (e: ScriptParseException) {
            Pair(false, e.message ?: "Syntax Error")
        } catch (e: Exception) {
            Pair(false, e.localizedMessage ?: "Parse Error")
        }
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
                text = "Run AI Edit Script / Code",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Paste generated CutPro DSL or JSON script from ChatGPT, Claude, Gemini, or DeepSeek to auto-edit your timeline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            // Syntax Validator Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = if (validationResult.first) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = validationResult.second,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (validationResult.first) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onErrorContainer
                    },
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Script Editor TextField
            OutlinedTextField(
                value = scriptText,
                onValueChange = { scriptText = it },
                label = { Text("CutPro Script / DSL Code") },
                placeholder = { Text("// Paste script here...") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                minLines = 8
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Helper Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        scriptText = """
                        // CutPro Sample Video Edit
                        SPLIT(track="main", time="00:02.000")
                        DELETE_RANGE(track="main", start="00:02.000", end="00:03.500", ripple=true)
                        APPLY_ZOOM_PUNCH(time="00:05.000", factor=1.20)
                        ADD_CAPTION(text="Clean Native Edit", start="00:00.000", end="00:02.000", style="HIGHLIGHT_PILL", lang="en")
                        APPLY_FILTER(filter="CINEMATIC_TEAL_ORANGE")
                        """.trimIndent()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Sample Script")
                }

                OutlinedButton(
                    onClick = { scriptText = "" },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Main Action Button
            Button(
                onClick = {
                    if (validationResult.first) {
                        onExecuteScript(scriptText)
                        onDismiss()
                        Toast.makeText(context, "AI Edit Script executed cleanly on timeline.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Fix syntax error before executing: ${validationResult.second}", Toast.LENGTH_LONG).show()
                    }
                },
                enabled = validationResult.first && scriptText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Run Auto-Edit Script on Timeline")
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

