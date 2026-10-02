package com.nandu.mymusic.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nandu.mymusic.ui.theme.AppleText
import com.nandu.mymusic.ui.theme.appleColors

/**
 * iOS-style section headers used across the library shelves (large title at the
 * top of a page, and the smaller overline that introduces a section).
 */

// ─── iOS-style section header with optional trailing content ──────────
@Composable
fun LargeTitleHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val C = appleColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = AppleText.LargeTitle, color = C.label)
        trailing?.invoke(this)
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: (@Composable RowScope.() -> Unit)? = null) {
    val C = appleColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, style = AppleText.Title2, color = C.label)
        trailing?.invoke(this)
    }
}
