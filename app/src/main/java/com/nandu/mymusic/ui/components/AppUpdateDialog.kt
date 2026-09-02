// mymusic/ui/components/AppUpdateDialog.kt
package com.nandu.mymusic.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.nandu.mymusic.ui.theme.appleColors

@Composable
fun AppUpdateDialog(
    latestVersion: String,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val C = appleColors()
    AlertDialog(
        onDismissRequest = { /* Prevent dismissing by clicking outside */ },
        containerColor = C.cardElevated,
        title = { Text("Update Available", color = C.label) },
        text = {
            Text(
                "Version $latestVersion is now available! Update now for new features and better performance.",
                color = C.secondaryLabel
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdateClick) {
                Text("Update Now", color = C.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = C.secondaryLabel)
            }
        }
    )
}
