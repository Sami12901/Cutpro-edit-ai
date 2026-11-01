package com.cutpro.core.designsystem.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.cutpro.core.designsystem.icon.CutProIcons

/**
 * Standard confirmation dialog for destructive or critical actions (e.g. project deletion).
 * Zero emojis permitted anywhere.
 */
@Composable
fun CutProConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    icon: ImageVector? = CutProIcons.Delete
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        icon = if (icon != null) {
            { Icon(imageVector = icon, contentDescription = null) }
        } else null,
        confirmButton = {
            CutProButton(
                text = confirmText,
                onClick = onConfirm
            )
        },
        dismissButton = {
            CutProOutlinedButton(
                text = dismissText,
                onClick = onDismiss
            )
        },
        modifier = modifier
    )
}
