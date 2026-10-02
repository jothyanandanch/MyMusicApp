package com.nandu.mymusic.ui.screens.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.model.Song
import com.nandu.mymusic.ui.components.AlbumArt
import com.nandu.mymusic.ui.theme.appleColors

/**
 * The "Playing Next" queue row (swipe-to-delete + drag reorder) and the
 * sleep-timer bottom sheet used by the Now Playing screen.
 */

// -- Inline queue row: swipe-to-remove + long-press the grabber to reorder.
//    Reorder is driven per-row by the parent's shared drag state; this composable
//    renders the row, follows the drag (translationY) and dims siblings while a
//    drag is live. Horizontal swipe reveals Delete; the grabber starts reorder.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InlineQueueRow(
    song: Song,
    onClick: () -> Unit,
    onRemove: () -> Unit = {},
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    shiftY: Float = 0f,
    alpha: Float = 1f,
    reorderModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val C = appleColors()
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                return@rememberSwipeToDismissBoxState true
            }
            false
        }
    )
    val scale by animateFloatAsState(
        targetValue = if (isDragging) 1.04f else 1f,
        animationSpec = if (isDragging) spring(stiffness = 500f, dampingRatio = 0.8f) else tween(150),
        label = "dragScale"
    )
    SwipeToDismissBox(
        state = dismissState, enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(targetValue = if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) C.destructive else Color.Transparent, label = "swipeColor")
            Box(modifier = Modifier.fillMaxSize().background(color, RoundedCornerShape(8.dp)).padding(end = 16.dp), contentAlignment = Alignment.CenterEnd) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White)
            }
        }
    ) {
        Box(modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                shadowElevation = if (isDragging) 12f else 0f
                shape = RoundedCornerShape(8.dp)
                clip = false
                this.translationY = dragOffsetY + shiftY
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick() }
                    .padding(vertical = 8.dp)
                    .then(reorderModifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AlbumArt(song = song, audioUri = song.uri, isActive = false, size = 48.dp, cornerRadius = 6.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = song.title ?: "Unknown Title", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(text = song.artist ?: "Unknown Artist", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                // Apple Music grabber handle — visual affordance for the long-press
                // vertical drag (reorder) that the whole row accepts. A sideways
                // swipe still removes the row via the SwipeToDismissBox.
                IconButton(
                    onClick = {},
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DragIndicator,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) C.accent else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * Apple Music wording for the live sleep-timer subtitle:
 *   "Sleeping for 9m 53s"  /  "Sleeping until queue ends"
 * Shown in the menu label and the sheet header while a timer is active.
 */
@Composable
fun sleepTimerStatusLabel(
    active: Boolean,
    remainingMs: Long,
    tracksRemaining: Int
): String? {
    if (!active) return null
    return when {
        tracksRemaining > 0 -> "Sleeping until queue ends ($tracksRemaining left)"
        tracksRemaining == 0 -> "Sleeping until queue ends"
        remainingMs > 0 -> {
            val totalSecs = (remainingMs + 999L) / 1000L
            val m = totalSecs / 60
            val s = totalSecs % 60
            "Sleeping for ${m}m ${s}s"
        }
        else -> "Sleeping until queue ends"
    }
}

// -- Apple Music-style Sleep Timer sheet --------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepTimerSheet(
    isTimerActive: Boolean,
    selectedOption: String?,
    liveStatus: String? = null,
    onSelectOption: (String) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val C = appleColors()

    val options = listOf(
        "When Current Song Ends", "End of Queue",
        "5 Minutes", "10 Minutes", "15 Minutes",
        "30 Minutes", "45 Minutes", "1 Hour", "2 Hours"
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = Color.Transparent) {
        Box(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Live subtitle while active — Apple places it just above the card.
                if (isTimerActive && liveStatus != null) {
                    Text(
                        liveStatus,
                        color = C.secondaryLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }

                // Centered grouped card — Apple Music's round, elevated surface.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.cardElevated)
                ) {
                    options.forEachIndexed { index, label ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectOption(label) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, color = C.label, fontSize = 17.sp, fontWeight = FontWeight.Normal, modifier = Modifier.weight(1f))
                            AnimatedVisibility(
                                visible = selectedOption == label && isTimerActive,
                                enter = scaleIn(tween(150)) + fadeIn(tween(150)),
                                exit = scaleOut(tween(100)) + fadeOut(tween(100))
                            ) {
                                Icon(Icons.Filled.Check, null, tint = C.accent, modifier = Modifier.size(20.dp))
                            }
                        }
                        if (index < options.size - 1) {
                            HorizontalDivider(color = C.separator.copy(alpha = 0.5f), thickness = 0.5.dp, modifier = Modifier.padding(start = 16.dp))
                        }
                    }
                }

                // Cancel — Apple places it as a top-aligned text button above the
                // card when a timer is running; here pinned above the card so it
                // scrolls with the sheet content.
                if (isTimerActive) {
                    TextButton(
                        onClick = { onCancelTimer(); onDismiss() },
                        modifier = Modifier.padding(top = 14.dp)
                    ) {
                        Text("Cancel", color = C.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
