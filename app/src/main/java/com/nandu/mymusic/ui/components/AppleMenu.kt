package com.nandu.mymusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.ui.theme.appleColors

/** iOS system red, used for destructive actions. */
val AppleDestructiveRed = Color(0xFFFF453A)

/** A single Apple-style menu action. */
data class AppleMenuAction(
    val label: String,
    val icon: ImageVector? = null,
    val isDestructive: Boolean = false,
    val isToggled: Boolean = false, // shows a trailing ✓ (enabled/selected states)
    val onClick: () -> Unit
)

/**
 * Apple-style context menu: a compact popover with icon + label rows, hairline
 * separators, iOS red for destructive actions and a trailing check for toggled
 * states. Visually matches Apple Music's ⋮ menus (13dp rounded card) while
 * keeping Material DropdownMenu's battle-tested on-screen positioning.
 */
@Composable
fun AppleMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<AppleMenuAction>,
    modifier: Modifier = Modifier
) {
    val C = appleColors()
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .background(C.cardElevated, RoundedCornerShape(13.dp))
            .heightIn(max = 420.dp),
        shape = RoundedCornerShape(13.dp),
        containerColor = C.cardElevated,
        tonalElevation = 0.dp,
        shadowElevation = 16.dp
    ) {
        actions.forEachIndexed { index, action ->
            if (index > 0) HorizontalDivider(color = C.separator.copy(alpha = 0.4f), thickness = 0.5.dp)
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (action.icon != null) {
                            Icon(
                                action.icon,
                                contentDescription = null,
                                tint = if (action.isDestructive) AppleDestructiveRed else C.label,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            action.label,
                            color = if (action.isDestructive) AppleDestructiveRed else C.label,
                            fontSize = 15.sp,
                            fontWeight = if (action.isToggled) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (action.isToggled) {
                            Spacer(Modifier.width(8.dp))
                            Text("✓", color = C.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                onClick = {
                    onDismissRequest()
                    action.onClick()
                }
            )
        }
    }
}
