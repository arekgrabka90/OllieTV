package com.ollie.iptv.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun OllieMaterialTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OllieAccent,
            onPrimary = OllieAccentInk,
            primaryContainer = Color(0xFF26394B),
            onPrimaryContainer = OllieTextPrimary,
            secondary = OllieAccent,
            onSecondary = OllieAccentInk,
            secondaryContainer = Color(0xFF202D3A),
            onSecondaryContainer = OllieTextPrimary,
            background = Ink,
            onBackground = OllieTextPrimary,
            surface = Color(0xFF10151D),
            onSurface = OllieTextPrimary,
            surfaceVariant = Color(0xFF1A222C),
            onSurfaceVariant = OllieTextSecondary,
            outline = OllieGlassLine,
            outlineVariant = Color.White.copy(alpha = 0.07f),
            error = OllieLiveRed,
            onError = Color(0xFF24080C)
        ),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp),
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(18.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}

@Composable
fun OllieGlassPanel(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = if (selected) Color(0xE626394B) else OllieGlass,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, if (selected) OllieAccent.copy(alpha = 0.38f) else OllieGlassLine),
        shadowElevation = if (selected) 14.dp else 9.dp
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}
