package com.ollie.iptv.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ollie.iptv.ui.theme.Muted

@Composable
fun OllieNavigationItem(
    tab: ScreenTab,
    selected: Boolean,
    compact: Boolean = false,
    bottom: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val icon = when (tab) {
        ScreenTab.LIVE -> Icons.Default.Tv
        ScreenTab.GUIDE -> Icons.Default.CalendarMonth
        ScreenTab.MOVIES -> Icons.Default.Movie
        ScreenTab.SERIES -> Icons.Default.VideoLibrary
        ScreenTab.FAVORITES -> Icons.Default.Star
        ScreenTab.RECORDINGS -> Icons.Default.FiberManualRecord
        ScreenTab.PLAYLISTS -> Icons.Default.PlaylistPlay
        ScreenTab.SETTINGS -> Icons.Default.Settings
    }
    Surface(
        onClick = onClick,
        color = when {
            selected -> Color(0xFF28313B)
            focused -> Color(0xFF202832)
            else -> Color.Transparent
        },
        shape = RoundedCornerShape(10.dp),
        modifier = (if (bottom) Modifier.width(68.dp).padding(horizontal = 1.dp)
            else if (compact) Modifier.width(64.dp).padding(vertical = 3.dp)
            else Modifier.fillMaxWidth().padding(vertical = 3.dp))
            .onFocusChanged { focused = it.isFocused }
    ) {
        if (compact || bottom) {
            Column(
                Modifier.padding(horizontal = 3.dp, vertical = if (bottom) 5.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, null, tint = if (selected || focused) Color.White else Muted)
                if (bottom) {
                    Text(
                        tab.label,
                        color = if (selected || focused) Color.White else Muted,
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        } else {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = if (selected || focused) Color.White else Muted)
                Spacer(Modifier.width(12.dp))
                Text(
                    tab.label,
                    color = if (selected || focused) Color.White else Muted
                )
            }
        }
    }
}
