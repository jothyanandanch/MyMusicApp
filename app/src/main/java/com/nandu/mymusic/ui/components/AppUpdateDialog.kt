// mymusic/ui/components/AppUpdateDialog.kt
package com.nandu.mymusic.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.nandu.mymusic.ui.screens.library.SpotifyGreen
import com.nandu.mymusic.ui.screens.library.SpotifySurface2
import com.nandu.mymusic.ui.screens.library.SpotifyWhite

@Composable
fun AppUpdateDialog(
    latestVersion: String,
    onUpdateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismissing by clicking outside */ },
        containerColor = SpotifySurface2,
        title = { Text("Update Available", color = SpotifyWhite) },
        text = {
            Text(
                "Version $latestVersion is now available! Update now for new features and better performance.",
                color = SpotifyWhite
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdateClick) {
                Text("Update Now", color = SpotifyGreen)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now", color = Color.Gray)
            }
        }
    )
}