package com.nexus.ai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun NexusTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFFA78BFA), secondary = Color(0xFF22D3EE),
        background = Color(0xFF07070F), surface = Color(0xFF16132E),
        onBackground = Color(0xFFF1EEFF), onSurface = Color(0xFFF1EEFF),
        outline = Color(0xFF3B3566), error = Color(0xFFFF6B81)
    ) else lightColorScheme(
        primary = Color(0xFF6D28D9), secondary = Color(0xFF0891B2),
        background = Color(0xFFF5F2FF), surface = Color(0xFFFFFFFF),
        onBackground = Color(0xFF14122B), onSurface = Color(0xFF14122B),
        outline = Color(0xFFD4CCF5), error = Color(0xFFD62F4B)
    )
    val bg = if (dark) listOf(Color(0xFF1B1347), Color(0xFF0A0818), Color(0xFF05050D))
    else listOf(Color(0xFFE6DEFF), Color(0xFFF5F2FF), Color(0xFFFFFFFF))
    MaterialTheme(colorScheme = scheme) {
        Surface(
            Modifier.fillMaxSize().background(Brush.verticalGradient(bg)),
            color = Color.Transparent, contentColor = scheme.onBackground
        ) { content() }
    }
}

/** Glass-style card. */
@Composable
fun Glass(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(14.dp), content = content)
        }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, border = border) {
            Column(Modifier.padding(14.dp), content = content)
        }
    }
}

@Composable
fun RoundIconButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape, modifier = Modifier.size(44.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) { Box(contentAlignment = Alignment.Center) { Text(label) } }
}
