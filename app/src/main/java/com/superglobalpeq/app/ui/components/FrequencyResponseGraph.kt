package com.superglobalpeq.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superglobalpeq.app.R
import com.superglobalpeq.app.dsp.FrequencyResponseCurves
import com.superglobalpeq.app.model.EqPreset
import com.superglobalpeq.app.model.PeqChannelTarget
import com.superglobalpeq.app.ui.icons.LucideIcons
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FrequencyResponseGraphCard(
    preset: EqPreset,
    curves: FrequencyResponseCurves?,
    editingTarget: PeqChannelTarget,
    bypassed: Boolean,
    channelSwap: Boolean,
    onAutoPreampClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    val colorCommon = MaterialTheme.colorScheme.secondary
    val colorLeft = Color(0xFF38BDF8)   // Vivid cyan-blue for Left total
    val colorRight = Color(0xFFFB7185)  // Vivid rose-coral for Right total

    val peakTotal = curves?.let { max(it.peakLeftDb, it.peakRightDb) } ?: 0.0
    val headroomOk = peakTotal <= 0.15

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = LucideIcons.AudioWaveform,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.freq_response_title),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = 14.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        softWrap = false,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Material 3 Expressive connected status pills (Bypass + Peak headroom)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    val peakStartCorner by animateDpAsState(
                        targetValue = if (bypassed) 5.dp else 12.dp,
                        label = "peakStartCorner"
                    )
                    AnimatedVisibility(
                        visible = bypassed,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally(),
                    ) {
                        val bypassShape = RoundedCornerShape(
                            topStart = 12.dp,
                            bottomStart = 12.dp,
                            topEnd = 5.dp,
                            bottomEnd = 5.dp
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant
                            ),
                            shape = bypassShape,
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(24.dp)
                                    .padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = LucideIcons.VolumeX,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(
                                    text = stringResource(R.string.badge_bypass),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp,
                                    ),
                                    softWrap = false,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    val peakBg by animateColorAsState(
                        targetValue = if (headroomOk) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        label = "peakBadgeBg"
                    )
                    val peakFg by animateColorAsState(
                        targetValue = if (headroomOk) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onError
                        },
                        label = "peakBadgeFg"
                    )
                    val peakShape = RoundedCornerShape(
                        topStart = peakStartCorner,
                        bottomStart = peakStartCorner,
                        topEnd = 12.dp,
                        bottomEnd = 12.dp
                    )
                    Surface(
                        color = peakBg,
                        contentColor = peakFg,
                        shape = peakShape,
                        modifier = Modifier
                            .clip(peakShape)
                            .clickable(enabled = !headroomOk, onClick = onAutoPreampClick)
                    ) {
                        Row(
                            modifier = Modifier
                                .height(24.dp)
                                .padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = if (headroomOk) LucideIcons.Check else LucideIcons.Sparkles,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = stringResource(R.string.badge_peak_db, peakTotal),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFeatureSettings = "tnum",
                                ),
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Icon(
                    imageVector = if (expanded) LucideIcons.ChevronUp else LucideIcons.ChevronDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (expanded) {
                Spacer(Modifier.height(10.dp))

                // Legend row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendPill(
                        color = colorLeft,
                        label = stringResource(R.string.legend_total_l),
                        dashed = false
                    )
                    LegendPill(
                        color = colorRight,
                        label = stringResource(R.string.legend_total_r),
                        dashed = false
                    )
                    LegendPill(
                        color = colorCommon,
                        label = stringResource(R.string.legend_common_lr),
                        dashed = true
                    )
                    Spacer(Modifier.weight(1f))
                    if (channelSwap) {
                        Text(
                            text = stringResource(R.string.badge_lr_swap),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            softWrap = false,
                            maxLines = 1
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                    val zeroLineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)
                    val nodeAccent = when (editingTarget) {
                        PeqChannelTarget.COMMON -> colorCommon
                        PeqChannelTarget.LEFT -> colorLeft
                        PeqChannelTarget.RIGHT -> colorRight
                    }

                    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        val w = size.width
                        val h = size.height
                        val minDb = -18f
                        val maxDb = 12f

                        fun freqToX(freq: Double): Float {
                            val clamped = freq.coerceIn(20.0, 20000.0)
                            val norm = (log10(clamped / 20.0) / 3.0).toFloat()
                            return norm * w
                        }

                        fun dbToY(db: Float): Float {
                            val clamped = db.coerceIn(minDb, maxDb)
                            val norm = (clamped - minDb) / (maxDb - minDb)
                            return h - norm * h
                        }

                        // Vertical log frequency gridlines
                        val gridFreqs = doubleArrayOf(
                            30.0, 50.0, 100.0, 200.0, 500.0,
                            1000.0, 2000.0, 5000.0, 10000.0, 20000.0
                        )
                        for (gf in gridFreqs) {
                            val x = freqToX(gf)
                            drawLine(
                                color = gridColor,
                                start = Offset(x, 0f),
                                end = Offset(x, h),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // Horizontal dB gridlines (-12, -6, 0, +6)
                        val gridDbs = floatArrayOf(-12f, -6f, 0f, 6f)
                        for (gdb in gridDbs) {
                            val y = dbToY(gdb)
                            drawLine(
                                color = if (gdb == 0f) zeroLineColor else gridColor,
                                start = Offset(0f, y),
                                end = Offset(w, y),
                                strokeWidth = if (gdb == 0f) 1.5.dp.toPx() else 1.dp.toPx()
                            )
                        }

                        if (curves != null) {
                            val freqs = curves.frequenciesHz

                            fun buildCurvePath(values: FloatArray): Path {
                                val p = Path()
                                for (i in freqs.indices) {
                                    val x = freqToX(freqs[i].toDouble())
                                    val y = dbToY(values[i])
                                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                                }
                                return p
                            }

                            val commonPath = buildCurvePath(curves.commonDb)
                            val leftPath = buildCurvePath(curves.totalLeftDb)
                            val rightPath = buildCurvePath(curves.totalRightDb)

                            // Soft area fill under Total Left curve
                            val fillPath = Path().apply {
                                addPath(leftPath)
                                lineTo(w, dbToY(0f))
                                lineTo(0f, dbToY(0f))
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        colorLeft.copy(alpha = 0.22f),
                                        colorLeft.copy(alpha = 0.0f)
                                    )
                                )
                            )

                            // Common L+R dashed reference curve
                            drawPath(
                                path = commonPath,
                                color = colorCommon.copy(alpha = 0.72f),
                                style = Stroke(
                                    width = 1.8.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                )
                            )

                            // Total Right curve
                            drawPath(
                                path = rightPath,
                                color = colorRight.copy(alpha = if (bypassed) 0.35f else 0.92f),
                                style = Stroke(
                                    width = 2.4.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Total Left curve
                            drawPath(
                                path = leftPath,
                                color = colorLeft.copy(alpha = if (bypassed) 0.35f else 0.95f),
                                style = Stroke(
                                    width = 2.6.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            // Active channel filter band nodes
                            val targetBands = preset.bandsFor(editingTarget).filter { it.enabled }
                            val targetPreamp = preset.preampFor(editingTarget)
                            val effectiveBase = when (editingTarget) {
                                PeqChannelTarget.COMMON -> targetPreamp
                                PeqChannelTarget.LEFT, PeqChannelTarget.RIGHT ->
                                    preset.commonPreampDb + targetPreamp
                            }
                            for (band in targetBands) {
                                val x = freqToX(band.frequencyHz)
                                val displayDb = if (band.type.supportsGain) {
                                    (band.gainDb + effectiveBase).toFloat()
                                } else {
                                    effectiveBase.toFloat()
                                }
                                val y = dbToY(displayDb)
                                drawCircle(
                                    color = nodeAccent.copy(alpha = 0.3f),
                                    radius = 6.5.dp.toPx(),
                                    center = Offset(x, y)
                                )
                                drawCircle(
                                    color = nodeAccent,
                                    radius = 3.5.dp.toPx(),
                                    center = Offset(x, y)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("20Hz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("100Hz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("1kHz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("10kHz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("20kHz", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!headroomOk) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onAutoPreampClick)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.freq_peak_warning_tap, peakTotal),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = LucideIcons.Sparkles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendPill(color: Color, label: String, dashed: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(if (dashed) 12.dp else 10.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = if (dashed) 0.7f else 1f))
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
            maxLines = 1
        )
    }
}
