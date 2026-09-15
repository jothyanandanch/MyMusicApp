package com.nandu.mymusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.ui.theme.AppleText
import com.nandu.mymusic.ui.theme.appleColors

/** A single iOS alert action. */
data class IosAlertAction(
    val label: String,
    val onClick: () -> Unit,
    val isDestructive: Boolean = false,
    val isDefault: Boolean = false
)

/**
 * iOS-style alert dialog: centered bold title, centered message, vertical
 * action stack separated by hairlines. Matches UIAlertController visuals.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IosAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    message: String? = null,
    actions: List<IosAlertAction>,
    content: (@Composable () -> Unit)? = null
) {
    val C = appleColors()
    BasicAlertDialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .widthIn(min = 270.dp, max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(C.cardElevated),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        title,
                        color = C.label,
                        style = AppleText.Headline,
                        textAlign = TextAlign.Center
                    )
                    if (message != null) {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
                        Text(
                            message,
                            color = C.label,
                            style = AppleText.Footnote,
                            textAlign = TextAlign.Center
                        )
                    }
                    content?.let {
                        androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 8.dp))
                        it()
                    }
                }
            }
            // Long action lists (e.g. the sleep-timer picker) must scroll
            // instead of clipping off-screen on small devices.
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                actions.forEach { action ->
                    HorizontalDivider(color = C.separator, thickness = 0.5.dp)
                    TextButton(
                        onClick = action.onClick,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            action.label,
                            color = when {
                                action.isDestructive -> C.destructive
                                action.isDefault -> C.accent
                                else -> C.accent
                            },
                            fontWeight = if (action.isDefault) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 17.sp
                        )
                    }
                }
            }
        }
    }
}
