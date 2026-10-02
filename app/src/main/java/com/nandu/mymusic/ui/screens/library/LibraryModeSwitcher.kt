package com.nandu.mymusic.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nandu.mymusic.ui.theme.appleColors

/**
 * Library mode segmented control (Local / Cloud / Hybrid) plus the data-saver
 * switch and the hybrid local-only / cloud-only toggles.
 */
@Composable
fun ModeSwitcher(
    libraryMode: Int,
    onModeChange: (Int) -> Unit,
    isDataSaverMode: Boolean,
    onDataSaverChange: (Boolean) -> Unit,
    hybridLocalOnly: Boolean,
    hybridOnlineOnly: Boolean,
    onHybridLocalOnly: (Boolean) -> Unit,
    onHybridOnlineOnly: (Boolean) -> Unit
) {
    val C = appleColors()
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(9.dp))
                .background(C.fill)
                .padding(2.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                val modes = listOf("Local", "Cloud", "Hybrid")
                modes.forEachIndexed { index, modeTitle ->
                    val isSelected = libraryMode == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(7.dp))
                            .background(if (isSelected) C.card else Color.Transparent)
                            .clickable { onModeChange(index) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = modeTitle,
                            color = C.label,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DataUsage, "Data Saver", tint = if (isDataSaverMode) C.accent else C.secondaryLabel, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Data Saver", color = C.secondaryLabel, fontSize = 13.sp)
            }
            Switch(
                checked = isDataSaverMode, onCheckedChange = onDataSaverChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = C.systemGreen,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = C.fill,
                    uncheckedBorderColor = Color.Transparent
                ),
                modifier = Modifier.scale(0.75f)
            )
        }
        if (libraryMode == 2) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Local Only toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (hybridLocalOnly) C.accent else C.fill)
                        .clickable { onHybridLocalOnly(!hybridLocalOnly) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Local Only",
                        color = if (hybridLocalOnly) Color.White else C.secondaryLabel,
                        fontWeight = if (hybridLocalOnly) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
                // Cloud Only toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (hybridOnlineOnly) C.accent else C.fill)
                        .clickable { onHybridOnlineOnly(!hybridOnlineOnly) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Cloud Only",
                        color = if (hybridOnlineOnly) Color.White else C.secondaryLabel,
                        fontWeight = if (hybridOnlineOnly) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
