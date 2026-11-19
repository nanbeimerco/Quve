package com.superglobalpeq.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val QuveAccentOrange = Color(0xFFFF5935)

private const val QuveGlyphPathData =
    "M202 371V506A248 248 0 0 0 450 754H540.45C533.55 742.54 527.2 730.55 521.48 718.08C515.76 705.61 510.68 692.67 506.29 679.3C501.9 665.93 498.21 652.15 495.23 638H450A132 132 0 0 1 318 506V371ZM822 371V546A208 208 0 0 1 769.1 684.59C758.78 676.57 749.99 668.21 742.5 659.37C735.02 650.54 728.81 641.25 723.58 630.82C718.36 620.39 714.1 608.77 711 594.94C707.9 581.11 706 565.04 706 546V371ZM370 186H486V586H450A80 80 0 0 1 370 506ZM538 186H654V546C654 563.05 655.3 578.61 657.66 592.89C660.01 607.17 663.42 620.17 667.71 632.14C672 644.12 677.17 655.08 683.16 665.29C689.15 675.51 695.96 684.98 703.59 693.88C711.23 702.79 719.69 711.13 728.96 718.98C738.23 726.82 748.32 734.18 759.14 741.07L696.86 838.93C682.02 829.49 667.66 819.06 654.01 807.51C640.37 795.97 627.44 783.29 615.52 769.38C603.6 755.47 592.68 740.33 583.09 723.96C573.49 707.59 565.22 689.99 558.51 671.27C551.8 652.54 546.65 632.68 543.2 611.78C539.76 590.89 538 568.95 538 546Z"

private val QuveGlyphPath: Path by lazy {
    PathParser().parsePathString(QuveGlyphPathData).toPath()
}

@Composable
fun QuveGlyphMark(
    modifier: Modifier = Modifier,
    tint: Color = QuveAccentOrange,
) {
    Canvas(modifier = modifier) {
        val glyphScale = minOf(size.width, size.height) / 1024f
        val ox = (size.width - 1024f * glyphScale) * 0.5f
        val oy = (size.height - 1024f * glyphScale) * 0.5f
        translate(ox, oy) {
            scale(glyphScale, glyphScale, pivot = Offset.Zero) {
                drawPath(path = QuveGlyphPath, color = tint)
            }
        }
    }
}

@Composable
fun QuveAppIconTile(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    backgroundColor: Color = Color.Black,
    glyphColor: Color = QuveAccentOrange,
    shape: Shape = RoundedCornerShape(13.dp),
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        QuveGlyphMark(modifier = Modifier.fillMaxSize(), tint = glyphColor)
    }
}
