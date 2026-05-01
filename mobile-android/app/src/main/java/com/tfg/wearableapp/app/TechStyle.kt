package com.tfg.wearableapp.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object TechStyle {
    val bgTop = Color(0xFF08131C)
    val bgMid = Color(0xFF0F2534)
    val bgBottom = Color(0xFF112E2B)
    val panel = Color(0xFF0D1D25)
    val panelAlt = Color(0xFF091A24)
    val accent = Color(0xFF00B7FF)
    val accentSecondary = Color(0xFF35D8A6)
    val title = Color(0xFFF2FFFF)
    val body = Color(0xFFB8D7E7)
    val label = Color(0xFF9ADBE0)
    val faint = Color(0xFFA6C2CE)

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(bgTop, bgMid, bgBottom),
    )
}

@Composable
fun TechScreenBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.background(TechStyle.backgroundBrush),
        content = content,
    )
}

@Composable
fun TechPanelCard(
    modifier: Modifier = Modifier,
    alt: Boolean = false,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = if (alt) TechStyle.panelAlt else TechStyle.panel,
        ),
        shape = RoundedCornerShape(18),
        content = { content() },
    )
}
