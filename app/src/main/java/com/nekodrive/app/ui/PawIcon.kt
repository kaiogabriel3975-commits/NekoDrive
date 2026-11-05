package com.nekodrive.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Patinha do cabeçalho, desenhada em código (uma almofada e quatro dedos). */
@Composable
fun PawIcon(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        drawOval(color, topLeft = Offset(w * 0.22f, h * 0.50f), size = Size(w * 0.56f, h * 0.42f))
        listOf(
            Triple(0.14f, 0.38f, 0.10f),
            Triple(0.36f, 0.18f, 0.11f),
            Triple(0.64f, 0.18f, 0.11f),
            Triple(0.86f, 0.38f, 0.10f),
        ).forEach { (x, y, r) -> drawCircle(color, radius = w * r, center = Offset(w * x, h * y)) }
    }
}
