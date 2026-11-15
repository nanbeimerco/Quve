package com.superglobalpeq.app.ui.components

import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.superglobalpeq.app.data.QuveSettings

@Composable
fun statusBarSafeInset() = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * Soft status-bar readability fade without creating a hard top bar or band.
 * Floating top-right glass capsules sit cleanly beside/above scrolling content.
 */
@Composable
fun TopFadeScrim(
    pageColor: Color,
    modifier: Modifier = Modifier,
) {
    val reduceDynamicBlur by QuveSettings.reduceDynamicBlur.collectAsState()
    val height = statusBarSafeInset() + 30.dp
    val brush = remember(pageColor, reduceDynamicBlur) {
        val stops = 8
        val peak = if (reduceDynamicBlur) 0.92f else 0.78f
        Brush.verticalGradient(
            colorStops = Array(stops) { i ->
                val t = i / (stops - 1f)
                t to pageColor.copy(alpha = peak * (1f - EaseOutCubic.transform(t)))
            }
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush)
    )
}
