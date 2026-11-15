package com.superglobalpeq.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.dsp.FrequencyResponseCurves
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.PeqChannelTarget
import com.superglobalpeq.app.ui.haptics.Haptic
import com.superglobalpeq.app.ui.haptics.rememberHaptics
import com.superglobalpeq.app.ui.icons.LucideIcons
import com.superglobalpeq.app.ui.theme.LocalQuveDarkTheme
import java.util.Locale
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

internal enum class GraphSeries {
    COMMON,
    RIGHT,
    LEFT,
}

internal data class InspectReadout(
    val freqHz: Double,
    val commonDb: Float,
    val leftDb: Float,
    val rightDb: Float,
)

private class PlotBounds {
    var left = 0f
    var right = 1f
    var top = 0f
    var bottom = 1f
}

private const val MIN_FREQ_HZ = 20.0
private const val MAX_FREQ_HZ = 20000.0
private const val MIN_DB = -18f
private const val MAX_DB = 12f
private const val PREAMP_EPSILON = 1e-4

/**
 * Returns `true` only when Left or Right independent calibration actually modifies
 * the signal (at least one enabled band or non-zero independent preamp).
 */
internal fun hasIndependentChannelEq(preset: EqPreset): Boolean =
    preset.leftBands.any { it.enabled } ||
        preset.rightBands.any { it.enabled } ||
        abs(preset.leftPreampDb) > PREAMP_EPSILON ||
        abs(preset.rightPreampDb) > PREAMP_EPSILON

/**
 * Determines which curves should be rendered and in what z-order (back to front).
 * In Common-only mode, only the single linked Common curve is rendered so `Total L`
 * never sits in front of identical `Total R` / `Common L+R` responses.
 */
internal fun visibleSeriesOrder(
    hasIndependentLR: Boolean,
    editingTarget: PeqChannelTarget,
): List<GraphSeries> {
    if (!hasIndependentLR) return listOf(GraphSeries.COMMON)
    return when (editingTarget) {
        PeqChannelTarget.COMMON -> listOf(GraphSeries.LEFT, GraphSeries.RIGHT, GraphSeries.COMMON)
        PeqChannelTarget.LEFT -> listOf(GraphSeries.COMMON, GraphSeries.RIGHT, GraphSeries.LEFT)
        PeqChannelTarget.RIGHT -> listOf(GraphSeries.COMMON, GraphSeries.LEFT, GraphSeries.RIGHT)
    }
}

internal fun interpolateInspectReadout(
    curves: FrequencyResponseCurves,
    normX: Float,
): InspectReadout? {
    val freqs = curves.frequenciesHz
    if (freqs.isEmpty()) return null
    val t = normX.coerceIn(0f, 1f)
    val freqHz = MIN_FREQ_HZ * (MAX_FREQ_HZ / MIN_FREQ_HZ).pow(t.toDouble())

    val lastIndex = freqs.lastIndex
    if (lastIndex == 0) {
        return InspectReadout(freqHz, curves.commonDb[0], curves.totalLeftDb[0], curves.totalRightDb[0])
    }

    val exactIndex = t * lastIndex
    val i0 = exactIndex.toInt().coerceIn(0, lastIndex - 1)
    val i1 = i0 + 1
    val frac = (exactIndex - i0).coerceIn(0f, 1f)

    fun lerp(arr: FloatArray): Float = arr[i0] + (arr[i1] - arr[i0]) * frac

    return InspectReadout(
        freqHz = freqHz,
        commonDb = lerp(curves.commonDb),
        leftDb = lerp(curves.totalLeftDb),
        rightDb = lerp(curves.totalRightDb),
    )
}

internal fun formatInspectFrequency(freqHz: Double): String = when {
    freqHz >= 10000.0 -> String.format(Locale.US, "%.1f kHz", freqHz / 1000.0)
    freqHz >= 1000.0 -> String.format(Locale.US, "%.2f kHz", freqHz / 1000.0)
    freqHz >= 100.0 -> String.format(Locale.US, "%.0f Hz", freqHz)
    else -> String.format(Locale.US, "%.1f Hz", freqHz)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun FrequencyResponseGraphCard(
    preset: EqPreset,
    curves: FrequencyResponseCurves?,
    editingTarget: PeqChannelTarget,
    bypassed: Boolean,
    channelSwap: Boolean,
    onAutoPreampClick: () -> Unit,
    onSelectTarget: ((PeqChannelTarget) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }
    var inspectNormX by remember { mutableStateOf<Float?>(null) }

    val haptics = rememberHaptics()
    val isDark = LocalQuveDarkTheme.current
    val motionScheme = MaterialTheme.motionScheme

    val hasIndependentLR = remember(preset) { hasIndependentChannelEq(preset) }
    val seriesOrder = remember(hasIndependentLR, editingTarget) {
        visibleSeriesOrder(hasIndependentLR, editingTarget)
    }

    // Clear scrubbing cursor if curves disappear
    LaunchedEffect(curves == null) {
        if (curves == null) inspectNormX = null
    }

    val colorPrimary = MaterialTheme.colorScheme.primary
    val colorLeft = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val colorRight = if (isDark) Color(0xFFFB7185) else Color(0xFFE11D48)
    val colorCommonRef = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.80f)

    val peakTotal = curves?.let { max(it.peakLeftDb, it.peakRightDb) } ?: 0.0
    val headroomOk = peakTotal <= 0.15
    val totalEnabledBands = preset.commonBands.count { it.enabled } +
        preset.leftBands.count { it.enabled } +
        preset.rightBands.count { it.enabled }

    val headerBottomRadius by animateDpAsState(
        targetValue = if (expanded) 6.dp else 24.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "graphHeaderBottomRadius",
    )
    val footerBottomRadius by animateDpAsState(
        targetValue = if (headroomOk) 24.dp else 6.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "graphFooterBottomRadius",
    )
    val iconBadgeCorner by animateDpAsState(
        targetValue = if (expanded) 16.dp else 10.dp,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "graphIconBadgeCorner",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "graphChevronRotation",
    )
    val curveDimFactor by animateFloatAsState(
        targetValue = if (bypassed) 0.62f else 1f,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "graphBypassAlpha",
    )

    val inspectReadout = remember(curves, inspectNormX) {
        val nx = inspectNormX
        if (curves != null && nx != null) interpolateInspectReadout(curves, nx) else null
    }

    val headerShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = headerBottomRadius,
        bottomEnd = headerBottomRadius,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(2.5.dp),
    ) {
        // ── Block 1: Expressive Header Pill ───────────────────────────────
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = headerShape,
            modifier = Modifier
                .fillMaxWidth()
                .clip(headerShape)
                .clickable {
                    haptics.play(Haptic.Select)
                    expanded = !expanded
                },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    color = if (bypassed) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    },
                    shape = RoundedCornerShape(iconBadgeCorner),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = LucideIcons.AudioWaveform,
                            contentDescription = null,
                            tint = if (bypassed) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.freq_response_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val baseSubtitle = if (hasIndependentLR) {
                        stringResource(R.string.freq_subtitle_independent, totalEnabledBands)
                    } else {
                        stringResource(R.string.freq_subtitle_common, totalEnabledBands, preset.commonPreampDb)
                    }
                    val subtitle = if (!expanded && bypassed) {
                        "${stringResource(R.string.badge_bypass)} · $baseSubtitle"
                    } else {
                        baseSubtitle
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = if (!expanded && bypassed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Connected M3 Expressive control pair: Peak badge + Expand/Collapse toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    val peakBg by animateColorAsState(
                        targetValue = when {
                            !headroomOk -> MaterialTheme.colorScheme.error
                            bypassed -> MaterialTheme.colorScheme.surfaceContainerHighest
                            else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        },
                        animationSpec = motionScheme.defaultEffectsSpec(),
                        label = "peakBadgeBg",
                    )
                    val peakFg by animateColorAsState(
                        targetValue = when {
                            !headroomOk -> MaterialTheme.colorScheme.onError
                            bypassed -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.primary
                        },
                        animationSpec = motionScheme.defaultEffectsSpec(),
                        label = "peakBadgeFg",
                    )
                    val peakShape = RoundedCornerShape(
                        topStart = 15.dp,
                        bottomStart = 15.dp,
                        topEnd = 6.dp,
                        bottomEnd = 6.dp,
                    )
                    Surface(
                        color = peakBg,
                        contentColor = peakFg,
                        shape = peakShape,
                        modifier = Modifier
                            .clip(peakShape)
                            .clickable(enabled = !headroomOk) {
                                haptics.play(Haptic.Select)
                                onAutoPreampClick()
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .height(30.dp)
                                .padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (headroomOk) LucideIcons.Check else LucideIcons.Sparkles,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = stringResource(R.string.badge_peak_db, peakTotal),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }

                    val chevronShape = RoundedCornerShape(
                        topStart = 6.dp,
                        bottomStart = 6.dp,
                        topEnd = 15.dp,
                        bottomEnd = 15.dp,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = chevronShape,
                        modifier = Modifier
                            .height(30.dp)
                            .width(32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = LucideIcons.ChevronDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer { rotationZ = chevronRotation },
                            )
                        }
                    }
                }
            }
        }

        // ── Expandable Plot & Legend Deck ─────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                expandVertically(motionScheme.defaultSpatialSpec()),
            exit = fadeOut(motionScheme.defaultEffectsSpec()) +
                shrinkVertically(motionScheme.defaultSpatialSpec()),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.5.dp),
            ) {
                // ── Block 2: Plot Canvas with Exact Axis Labels & Scrubbing ─
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val plotBounds = remember { PlotBounds() }
                    val textMeasurer = rememberTextMeasurer()

                    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    val zeroAxisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.90f)
                    val majorGridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.62f)
                    val minorGridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                    val zeroGridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.90f)
                    val clippingTintColor = MaterialTheme.colorScheme.error.copy(alpha = 0.06f)
                    val nodeCutoutColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    val cursorLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)

                    val axisTextStyle = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum",
                        color = axisColor,
                    )
                    val zeroAxisTextStyle = axisTextStyle.copy(
                        fontWeight = FontWeight.Bold,
                        color = zeroAxisColor,
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(216.dp)
                            .pointerInput(curves != null) {
                                if (curves == null) return@pointerInput
                                detectTapGestures { offset ->
                                    val plotW = (plotBounds.right - plotBounds.left).coerceAtLeast(1f)
                                    if (offset.x < plotBounds.left || offset.x > plotBounds.right ||
                                        offset.y < plotBounds.top || offset.y > plotBounds.bottom
                                    ) {
                                        inspectNormX = null
                                        return@detectTapGestures
                                    }
                                    val norm = ((offset.x - plotBounds.left) / plotW).coerceIn(0f, 1f)
                                    val current = inspectNormX
                                    if (current != null && abs(current - norm) * plotW < 18.dp.toPx()) {
                                        inspectNormX = null
                                    } else {
                                        haptics.play(Haptic.Select)
                                        inspectNormX = norm
                                    }
                                }
                            }
                            .pointerInput(curves != null) {
                                if (curves == null) return@pointerInput
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        val plotW = (plotBounds.right - plotBounds.left).coerceAtLeast(1f)
                                        inspectNormX = ((offset.x - plotBounds.left) / plotW).coerceIn(0f, 1f)
                                    },
                                    onHorizontalDrag = { change, _ ->
                                        val plotW = (plotBounds.right - plotBounds.left).coerceAtLeast(1f)
                                        inspectNormX = ((change.position.x - plotBounds.left) / plotW).coerceIn(0f, 1f)
                                    },
                                )
                            },
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val sampleDbLayout = textMeasurer.measure("-18", style = axisTextStyle)
                            val sampleFreqLayout = textMeasurer.measure("20k", style = axisTextStyle)

                            val plotLeft = sampleDbLayout.size.width + 12.dp.toPx()
                            val plotTop = 12.dp.toPx()
                            val plotRight = (size.width - 12.dp.toPx()).coerceAtLeast(plotLeft + 40f)
                            val plotBottom = (size.height - sampleFreqLayout.size.height - 10.dp.toPx())
                                .coerceAtLeast(plotTop + 40f)

                            plotBounds.left = plotLeft
                            plotBounds.right = plotRight
                            plotBounds.top = plotTop
                            plotBounds.bottom = plotBottom

                            val plotW = plotRight - plotLeft
                            val plotH = plotBottom - plotTop

                            fun freqToX(freq: Double): Float {
                                val clamped = freq.coerceIn(MIN_FREQ_HZ, MAX_FREQ_HZ)
                                val norm = (log10(clamped / MIN_FREQ_HZ) / 3.0).toFloat()
                                return plotLeft + norm * plotW
                            }

                            fun dbToY(db: Float): Float {
                                val clamped = db.coerceIn(MIN_DB, MAX_DB)
                                val norm = (clamped - MIN_DB) / (MAX_DB - MIN_DB)
                                return plotBottom - norm * plotH
                            }

                            val zeroY = dbToY(0f)

                            if (!headroomOk) {
                                drawRect(
                                    color = clippingTintColor,
                                    topLeft = Offset(plotLeft, plotTop),
                                    size = androidx.compose.ui.geometry.Size(plotW, zeroY - plotTop),
                                )
                            }

                            // Vertical log frequency gridlines & pixel-accurate axis labels
                            val freqTicks = listOf(
                                20.0 to "20",
                                30.0 to null,
                                50.0 to "50",
                                100.0 to "100",
                                200.0 to "200",
                                500.0 to "500",
                                1000.0 to "1k",
                                2000.0 to "2k",
                                5000.0 to "5k",
                                10000.0 to "10k",
                                20000.0 to "20k",
                            )
                            val majorFreqSet = setOf(20.0, 100.0, 1000.0, 10000.0, 20000.0)
                            val minorDash = PathEffect.dashPathEffect(floatArrayOf(4f, 6f), 0f)
                            val labelY = plotBottom + 5.dp.toPx()
                            val maxFreqRightX = size.width - 6.dp.toPx()
                            val minLabelGap = 3.dp.toPx()
                            val rightEndLayout = textMeasurer.measure("20k", style = axisTextStyle)
                            val rightEndLeftX = (plotRight - rightEndLayout.size.width).coerceAtMost(
                                maxFreqRightX - rightEndLayout.size.width
                            )
                            var lastLabelRightX = Float.NEGATIVE_INFINITY

                            for ((freq, label) in freqTicks) {
                                val x = freqToX(freq)
                                val isMajor = freq in majorFreqSet
                                drawLine(
                                    color = if (isMajor) majorGridColor else minorGridColor,
                                    start = Offset(x, plotTop),
                                    end = Offset(x, plotBottom),
                                    strokeWidth = if (isMajor) 1.dp.toPx() else 0.75.dp.toPx(),
                                    pathEffect = if (isMajor) null else minorDash,
                                )

                                if (label != null) {
                                    val layout = textMeasurer.measure(label, style = axisTextStyle)
                                    val wPx = layout.size.width.toFloat()
                                    val drawX = when (freq) {
                                        20.0 -> plotLeft
                                        20000.0 -> rightEndLeftX
                                        else -> x - wPx * 0.5f
                                    }
                                    val fitsBeforeLast = freq == 20000.0 ||
                                        drawX + wPx + minLabelGap <= rightEndLeftX
                                    if (drawX >= lastLabelRightX + minLabelGap && fitsBeforeLast) {
                                        drawText(
                                            textLayoutResult = layout,
                                            topLeft = Offset(drawX, labelY),
                                        )
                                        lastLabelRightX = drawX + wPx
                                    }
                                }
                            }

                            // Horizontal dB gridlines & aligned Y-axis labels
                            val dbTicks = listOf(12f, 6f, 0f, -6f, -12f, -18f)
                            for (gdb in dbTicks) {
                                val y = dbToY(gdb)
                                val isZero = gdb == 0f
                                val isEdge = gdb == MAX_DB || gdb == MIN_DB
                                drawLine(
                                    color = when {
                                        isZero -> zeroGridColor
                                        isEdge -> minorGridColor
                                        else -> majorGridColor
                                    },
                                    start = Offset(plotLeft, y),
                                    end = Offset(plotRight, y),
                                    strokeWidth = if (isZero) 1.35.dp.toPx() else 0.85.dp.toPx(),
                                    pathEffect = if (isZero || isEdge) null else minorDash,
                                )

                                val label = when {
                                    gdb > 0f -> "+${gdb.toInt()}"
                                    gdb == 0f -> "0"
                                    else -> "${gdb.toInt()}"
                                }
                                val layout = textMeasurer.measure(
                                    text = label,
                                    style = if (isZero) zeroAxisTextStyle else axisTextStyle,
                                )
                                val textY = (y - layout.size.height * 0.5f)
                                    .coerceIn(0f, plotBottom - layout.size.height)
                                drawText(
                                    textLayoutResult = layout,
                                    topLeft = Offset(
                                        x = plotLeft - layout.size.width - 7.dp.toPx(),
                                        y = textY,
                                    ),
                                )
                            }

                            if (curves != null) {
                                val freqs = curves.frequenciesHz
                                clipRect(
                                    left = plotLeft - 2.dp.toPx(),
                                    top = plotTop - 4.dp.toPx(),
                                    right = plotRight + 2.dp.toPx(),
                                    bottom = plotBottom + 4.dp.toPx(),
                                ) {
                                    val commonPath = buildSmoothCurvePath(freqs, curves.commonDb, ::freqToX, ::dbToY)
                                    val leftPath = buildSmoothCurvePath(freqs, curves.totalLeftDb, ::freqToX, ::dbToY)
                                    val rightPath = buildSmoothCurvePath(freqs, curves.totalRightDb, ::freqToX, ::dbToY)

                                    val foregroundSeries = seriesOrder.last()
                                    val refDash = PathEffect.dashPathEffect(floatArrayOf(11f, 8f), 0f)

                                    for (series in seriesOrder) {
                                        val isForeground = series == foregroundSeries
                                        val strokeColor = when (series) {
                                            GraphSeries.COMMON -> if (hasIndependentLR) colorCommonRef else colorPrimary
                                            GraphSeries.LEFT -> colorLeft
                                            GraphSeries.RIGHT -> colorRight
                                        }
                                        val path = when (series) {
                                            GraphSeries.COMMON -> commonPath
                                            GraphSeries.LEFT -> leftPath
                                            GraphSeries.RIGHT -> rightPath
                                        }
                                        val isDashed = hasIndependentLR && series == GraphSeries.COMMON

                                        if (isForeground) {
                                            drawZeroAnchoredFill(
                                                curvePath = path,
                                                plotLeft = plotLeft,
                                                plotRight = plotRight,
                                                plotTop = plotTop,
                                                plotBottom = plotBottom,
                                                zeroY = zeroY,
                                                color = strokeColor,
                                                dimFactor = curveDimFactor * if (isDashed) 0.55f else 1f,
                                            )
                                            // Soft luminous halo behind foreground curve
                                            drawPath(
                                                path = path,
                                                color = strokeColor.copy(alpha = 0.14f * curveDimFactor),
                                                style = Stroke(
                                                    width = 6.5.dp.toPx(),
                                                    cap = StrokeCap.Round,
                                                    join = StrokeJoin.Round,
                                                    pathEffect = if (isDashed) refDash else null,
                                                ),
                                            )
                                        }

                                        val alpha = if (isForeground) {
                                            0.96f * curveDimFactor
                                        } else {
                                            0.64f * curveDimFactor
                                        }
                                        val strokeWidth = when {
                                            isForeground && !isDashed -> 2.8.dp.toPx()
                                            isForeground && isDashed -> 2.2.dp.toPx()
                                            isDashed -> 1.8.dp.toPx()
                                            else -> 2.1.dp.toPx()
                                        }

                                        drawPath(
                                            path = path,
                                            color = strokeColor.copy(alpha = alpha),
                                            style = Stroke(
                                                width = strokeWidth,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round,
                                                pathEffect = if (isDashed) refDash else null,
                                            ),
                                        )
                                    }

                                    // Active target filter band nodes
                                    val nodeAccent = when {
                                        !hasIndependentLR -> colorPrimary
                                        editingTarget == PeqChannelTarget.LEFT -> colorLeft
                                        editingTarget == PeqChannelTarget.RIGHT -> colorRight
                                        else -> colorCommonRef
                                    }
                                    val targetBands = preset.bandsFor(editingTarget).filter { it.enabled }
                                    val targetPreamp = preset.preampFor(editingTarget)
                                    val effectiveBase = when (editingTarget) {
                                        PeqChannelTarget.COMMON -> targetPreamp
                                        PeqChannelTarget.LEFT, PeqChannelTarget.RIGHT ->
                                            preset.commonPreampDb + targetPreamp
                                    }
                                    for (band in targetBands) {
                                        val bx = freqToX(band.frequencyHz)
                                        val displayDb = if (band.type.supportsGain) {
                                            (band.gainDb + effectiveBase).toFloat()
                                        } else {
                                            effectiveBase.toFloat()
                                        }
                                        val by = dbToY(displayDb)
                                        drawCircle(
                                            color = nodeAccent.copy(alpha = 0.22f * curveDimFactor),
                                            radius = 7.dp.toPx(),
                                            center = Offset(bx, by),
                                        )
                                        drawCircle(
                                            color = nodeCutoutColor,
                                            radius = 4.8.dp.toPx(),
                                            center = Offset(bx, by),
                                        )
                                        drawCircle(
                                            color = nodeAccent.copy(alpha = curveDimFactor),
                                            radius = 3.2.dp.toPx(),
                                            center = Offset(bx, by),
                                        )
                                    }

                                    // Interactive scrub crosshair & curve markers
                                    val readout = inspectReadout
                                    if (readout != null) {
                                        val cx = freqToX(readout.freqHz)
                                        drawLine(
                                            color = cursorLineColor,
                                            start = Offset(cx, plotTop),
                                            end = Offset(cx, plotBottom),
                                            strokeWidth = 1.2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
                                        )
                                        if (!hasIndependentLR) {
                                            val cy = dbToY(readout.commonDb)
                                            drawCircle(color = colorPrimary.copy(alpha = 0.28f), radius = 9.dp.toPx(), center = Offset(cx, cy))
                                            drawCircle(color = nodeCutoutColor, radius = 5.5.dp.toPx(), center = Offset(cx, cy))
                                            drawCircle(color = colorPrimary, radius = 3.8.dp.toPx(), center = Offset(cx, cy))
                                        } else {
                                            val cyL = dbToY(readout.leftDb)
                                            val cyR = dbToY(readout.rightDb)
                                            drawCircle(color = nodeCutoutColor, radius = 5.2.dp.toPx(), center = Offset(cx, cyR))
                                            drawCircle(color = colorRight, radius = 3.6.dp.toPx(), center = Offset(cx, cyR))
                                            drawCircle(color = nodeCutoutColor, radius = 5.2.dp.toPx(), center = Offset(cx, cyL))
                                            drawCircle(color = colorLeft, radius = 3.6.dp.toPx(), center = Offset(cx, cyL))
                                        }
                                    }
                                }
                            }
                        }

                        // Floating Expressive scrub readout pill (smart side-flip away from cursor)
                        val cursorNorm = inspectNormX
                        if (inspectReadout != null && cursorNorm != null) {
                            val pillAlignment = if (cursorNorm > 0.52f) Alignment.TopStart else Alignment.TopEnd
                            val freqText = formatInspectFrequency(inspectReadout.freqHz)
                            val valueText = if (hasIndependentLR) {
                                String.format(
                                    Locale.US,
                                    "L %+.1f · R %+.1f dB",
                                    inspectReadout.leftDb,
                                    inspectReadout.rightDb,
                                )
                            } else {
                                String.format(Locale.US, "%+.1f dB", inspectReadout.commonDb)
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = CircleShape,
                                modifier = Modifier
                                    .align(pillAlignment)
                                    .padding(top = 8.dp, start = 40.dp, end = 10.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        haptics.play(Haptic.Select)
                                        inspectNormX = null
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = "$freqText  $valueText",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFeatureSettings = "tnum",
                                        ),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                    Icon(
                                        imageVector = LucideIcons.X,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(11.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Block 3: Expressive Legend & Channel Pill Bar ──────────
                val footerShape = RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 6.dp,
                    bottomStart = footerBottomRadius,
                    bottomEnd = footerBottomRadius,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = footerShape,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FlowRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (!hasIndependentLR) {
                                ExpressiveLegendChip(
                                    color = colorPrimary,
                                    label = stringResource(R.string.legend_common_linked),
                                    dashed = false,
                                    selected = editingTarget == PeqChannelTarget.COMMON,
                                    onClick = onSelectTarget?.let { select ->
                                        {
                                            haptics.play(Haptic.Select)
                                            select(PeqChannelTarget.COMMON)
                                        }
                                    },
                                )
                            } else {
                                ExpressiveLegendChip(
                                    color = colorLeft,
                                    label = stringResource(R.string.legend_total_l),
                                    dashed = false,
                                    selected = editingTarget == PeqChannelTarget.LEFT,
                                    onClick = onSelectTarget?.let { select ->
                                        {
                                            haptics.play(Haptic.Select)
                                            select(PeqChannelTarget.LEFT)
                                        }
                                    },
                                )
                                ExpressiveLegendChip(
                                    color = colorRight,
                                    label = stringResource(R.string.legend_total_r),
                                    dashed = false,
                                    selected = editingTarget == PeqChannelTarget.RIGHT,
                                    onClick = onSelectTarget?.let { select ->
                                        {
                                            haptics.play(Haptic.Select)
                                            select(PeqChannelTarget.RIGHT)
                                        }
                                    },
                                )
                                ExpressiveLegendChip(
                                    color = colorCommonRef,
                                    label = stringResource(R.string.legend_common_lr),
                                    dashed = true,
                                    selected = editingTarget == PeqChannelTarget.COMMON,
                                    onClick = onSelectTarget?.let { select ->
                                        {
                                            haptics.play(Haptic.Select)
                                            select(PeqChannelTarget.COMMON)
                                        }
                                    },
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = bypassed,
                            enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                                expandHorizontally(motionScheme.fastSpatialSpec()),
                            exit = fadeOut(motionScheme.defaultEffectsSpec()) +
                                shrinkHorizontally(motionScheme.fastSpatialSpec()),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = CircleShape,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = LucideIcons.VolumeX,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.badge_bypass),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        softWrap = false,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }

                        if (channelSwap) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = CircleShape,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = LucideIcons.ArrowLeftRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.badge_lr_swap),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                        softWrap = false,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Block 4 (Conditional): Headroom Warning Action Bar ──────
                AnimatedVisibility(
                    visible = !headroomOk,
                    enter = fadeIn(motionScheme.defaultEffectsSpec()) +
                        expandVertically(motionScheme.fastSpatialSpec()),
                    exit = fadeOut(motionScheme.defaultEffectsSpec()) +
                        shrinkVertically(motionScheme.fastSpatialSpec()),
                ) {
                    val warnShape = RoundedCornerShape(
                        topStart = 6.dp,
                        topEnd = 6.dp,
                        bottomStart = 24.dp,
                        bottomEnd = 24.dp,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = warnShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(warnShape)
                            .clickable {
                                haptics.play(Haptic.Select)
                                onAutoPreampClick()
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = LucideIcons.Sparkles,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                            )
                            Text(
                                text = stringResource(R.string.freq_peak_warning_tap, peakTotal),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildSmoothCurvePath(
    freqs: FloatArray,
    values: FloatArray,
    freqToX: (Double) -> Float,
    dbToY: (Float) -> Float,
): Path {
    val path = Path()
    if (freqs.isEmpty() || values.isEmpty()) return path
    val count = minOf(freqs.size, values.size)
    var prevX = freqToX(freqs[0].toDouble())
    var prevY = dbToY(values[0])
    path.moveTo(prevX, prevY)
    for (i in 1 until count) {
        val x = freqToX(freqs[i].toDouble())
        val y = dbToY(values[i])
        val midX = (prevX + x) * 0.5f
        val midY = (prevY + y) * 0.5f
        path.quadraticTo(prevX, prevY, midX, midY)
        prevX = x
        prevY = y
    }
    path.lineTo(prevX, prevY)
    return path
}

private fun DrawScope.drawZeroAnchoredFill(
    curvePath: Path,
    plotLeft: Float,
    plotRight: Float,
    plotTop: Float,
    plotBottom: Float,
    zeroY: Float,
    color: Color,
    dimFactor: Float,
) {
    val plotH = (plotBottom - plotTop).coerceAtLeast(1f)
    val zeroStop = ((zeroY - plotTop) / plotH).coerceIn(0.05f, 0.95f)
    val fillPath = Path().apply {
        addPath(curvePath)
        lineTo(plotRight, zeroY)
        lineTo(plotLeft, zeroY)
        close()
    }
    drawPath(
        path = fillPath,
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to color.copy(alpha = 0.30f * dimFactor),
                zeroStop to color.copy(alpha = 0.03f * dimFactor),
                1.0f to color.copy(alpha = 0.24f * dimFactor),
            ),
            startY = plotTop,
            endY = plotBottom,
        ),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveLegendChip(
    color: Color,
    label: String,
    dashed: Boolean,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) {
            color.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "legendChipBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "legendChipFg",
    )

    Surface(
        color = bgColor,
        contentColor = textColor,
        border = if (selected) BorderStroke(1.dp, color.copy(alpha = 0.45f)) else null,
        shape = CircleShape,
        modifier = Modifier
            .clip(CircleShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            if (dashed) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(2.5.dp)
                            .clip(CircleShape)
                            .background(color),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .width(11.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(color),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                ),
                softWrap = false,
                maxLines = 1,
            )
        }
    }
}
