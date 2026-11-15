package com.superglobalpeq.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Bold Lucide vector icons (24x24 viewport, strokeWidth = 2.5f, round caps & joins).
 *
 * Vector paths derived from Lucide Icons (https://lucide.dev), licensed under the ISC License.
 * Copyright (c) for portions of Lucide are held by Cole Bemis 2013-2022 as part of Feather (MIT).
 * All other copyright (c) for Lucide are held by Lucide Contributors 2022.
 */
object LucideIcons {
    private const val BOLD_STROKE = 2.5f

    private fun lucideIcon(
        name: String,
        strokeWidth: Float = BOLD_STROKE,
        block: ImageVector.Builder.(SolidColor, Float) -> Unit
    ): ImageVector {
        val stroke = SolidColor(Color.Black)
        return ImageVector.Builder(
            name = "Lucide.$name",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            block(stroke, strokeWidth)
        }.build()
    }

    private fun ImageVector.Builder.strokePath(
        stroke: SolidColor,
        strokeWidth: Float = BOLD_STROKE,
        build: PathBuilder.() -> Unit
    ) {
        path(
            fill = null,
            stroke = stroke,
            strokeLineWidth = strokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = build
        )
    }

    val AudioWaveform: ImageVector by lazy {
        lucideIcon("AudioWaveform") { s, w ->
            strokePath(s, w) {
                moveTo(2f, 13f)
                arcTo(2f, 2f, 0f, false, false, 4f, 11f)
                verticalLineTo(7f)
                arcTo(2f, 2f, 0f, false, true, 8f, 7f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, false, false, 12f, 20f)
                verticalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 16f, 4f)
                verticalLineTo(17f)
                arcTo(2f, 2f, 0f, false, false, 20f, 17f)
                verticalLineTo(13f)
                arcTo(2f, 2f, 0f, false, true, 22f, 11f)
            }
        }
    }

    val SlidersHorizontal: ImageVector by lazy {
        lucideIcon("SlidersHorizontal") { s, w ->
            strokePath(s, w) {
                moveTo(21f, 4f); horizontalLineTo(14f)
                moveTo(10f, 4f); horizontalLineTo(3f)
                moveTo(21f, 12f); horizontalLineTo(12f)
                moveTo(8f, 12f); horizontalLineTo(3f)
                moveTo(21f, 20f); horizontalLineTo(16f)
                moveTo(12f, 20f); horizontalLineTo(3f)
                moveTo(14f, 2f); verticalLineTo(6f)
                moveTo(8f, 10f); verticalLineTo(14f)
                moveTo(16f, 18f); verticalLineTo(22f)
            }
        }
    }

    val Power: ImageVector by lazy {
        lucideIcon("Power") { s, w ->
            strokePath(s, w) {
                moveTo(12f, 2f); verticalLineTo(10f)
                moveTo(18.4f, 6.6f)
                arcTo(9f, 9f, 0f, true, true, 5.63f, 6.64f)
            }
        }
    }

    val ArrowLeftRight: ImageVector by lazy {
        lucideIcon("ArrowLeftRight") { s, w ->
            strokePath(s, w) {
                moveTo(8f, 3f); lineTo(4f, 7f); lineTo(8f, 11f)
                moveTo(4f, 7f); horizontalLineTo(20f)
                moveTo(16f, 21f); lineTo(20f, 17f); lineTo(16f, 13f)
                moveTo(20f, 17f); horizontalLineTo(4f)
            }
        }
    }

    val Speaker: ImageVector by lazy {
        lucideIcon("Speaker") { s, w ->
            strokePath(s, w) {
                // Rounded rectangle (4,2) -> (20,22), r=2
                moveTo(6f, 2f); horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 20f, 4f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 18f, 22f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 4f, 20f)
                verticalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 6f, 2f)
                close()
                // Tweeter dot
                moveTo(12f, 6f); lineTo(12.01f, 6f)
                // Woofer circle (12, 14, r=4)
                moveTo(16f, 14f)
                arcTo(4f, 4f, 0f, true, true, 8f, 14f)
                arcTo(4f, 4f, 0f, true, true, 16f, 14f)
                close()
            }
        }
    }

    val Headphones: ImageVector by lazy {
        lucideIcon("Headphones") { s, w ->
            strokePath(s, w) {
                moveTo(3f, 14f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, false, true, 8f, 16f)
                verticalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 6f, 21f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                verticalLineTo(12f)
                arcTo(9f, 9f, 0f, false, true, 21f, 12f)
                verticalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 16f, 19f)
                verticalLineTo(16f)
                arcTo(2f, 2f, 0f, false, true, 18f, 14f)
                horizontalLineTo(21f)
            }
        }
    }

    val Bluetooth: ImageVector by lazy {
        lucideIcon("Bluetooth") { s, w ->
            strokePath(s, w) {
                moveTo(7f, 7f)
                lineTo(17f, 17f)
                lineTo(12f, 22f)
                verticalLineTo(2f)
                lineTo(17f, 7f)
                lineTo(7f, 17f)
            }
        }
    }

    val Usb: ImageVector by lazy {
        lucideIcon("Usb") { s, w ->
            strokePath(s, w) {
                // Circle (10, 7, r=1)
                moveTo(11f, 7f)
                arcTo(1f, 1f, 0f, true, true, 9f, 7f)
                arcTo(1f, 1f, 0f, true, true, 11f, 7f)
                close()
                // Circle (4, 20, r=1)
                moveTo(5f, 20f)
                arcTo(1f, 1f, 0f, true, true, 3f, 20f)
                arcTo(1f, 1f, 0f, true, true, 5f, 20f)
                close()
                // Diagonal shaft
                moveTo(4.7f, 19.3f); lineTo(19f, 5f)
                moveTo(21f, 3f); lineTo(18f, 4f); lineTo(20f, 6f); close()
                moveTo(9.26f, 7.68f); lineTo(5f, 12f); lineTo(7f, 17f)
                moveTo(10f, 14f); lineTo(15f, 16f); lineTo(19.5f, 11.5f)
                moveTo(18f, 10f); lineTo(21f, 13f)
            }
        }
    }

    val ShieldCheck: ImageVector by lazy {
        lucideIcon("ShieldCheck") { s, w ->
            strokePath(s, w) {
                moveTo(20f, 13f)
                curveTo(20f, 18f, 16.5f, 20.5f, 12.34f, 21.95f)
                curveTo(12.12f, 22.02f, 11.88f, 22.02f, 11.66f, 21.95f)
                curveTo(7.5f, 20.5f, 4f, 18f, 4f, 13f)
                verticalLineTo(6f)
                arcTo(1f, 1f, 0f, false, true, 5f, 5f)
                curveTo(7f, 5f, 9.5f, 3.8f, 11.24f, 2.28f)
                arcTo(1.17f, 1.17f, 0f, false, true, 12.76f, 2.28f)
                curveTo(14.51f, 3.81f, 17f, 5f, 19f, 5f)
                arcTo(1f, 1f, 0f, false, true, 20f, 6f)
                close()
                moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
            }
        }
    }

    val ShieldAlert: ImageVector by lazy {
        lucideIcon("ShieldAlert") { s, w ->
            strokePath(s, w) {
                moveTo(20f, 13f)
                curveTo(20f, 18f, 16.5f, 20.5f, 12.34f, 21.95f)
                curveTo(12.12f, 22.02f, 11.88f, 22.02f, 11.66f, 21.95f)
                curveTo(7.5f, 20.5f, 4f, 18f, 4f, 13f)
                verticalLineTo(6f)
                arcTo(1f, 1f, 0f, false, true, 5f, 5f)
                curveTo(7f, 5f, 9.5f, 3.8f, 11.24f, 2.28f)
                arcTo(1.17f, 1.17f, 0f, false, true, 12.76f, 2.28f)
                curveTo(14.51f, 3.81f, 17f, 5f, 19f, 5f)
                arcTo(1f, 1f, 0f, false, true, 20f, 6f)
                close()
                moveTo(12f, 8f); verticalLineTo(12f)
                moveTo(12f, 16f); lineTo(12.01f, 16f)
            }
        }
    }

    val Layers: ImageVector by lazy {
        lucideIcon("Layers") { s, w ->
            strokePath(s, w) {
                moveTo(12.83f, 2.18f)
                arcTo(2f, 2f, 0f, false, false, 11.17f, 2.18f)
                lineTo(2.6f, 6.08f)
                arcTo(1f, 1f, 0f, false, false, 2.6f, 7.9f)
                lineTo(11.18f, 11.82f)
                arcTo(2f, 2f, 0f, false, false, 12.84f, 11.82f)
                lineTo(21.41f, 7.92f)
                arcTo(1f, 1f, 0f, false, false, 21.41f, 6.1f)
                close()
                moveTo(2f, 12f); lineTo(11.17f, 16.18f)
                arcTo(2f, 2f, 0f, false, false, 12.83f, 16.18f)
                lineTo(22f, 12f)
                moveTo(2f, 17f); lineTo(11.17f, 21.18f)
                arcTo(2f, 2f, 0f, false, false, 12.83f, 21.18f)
                lineTo(22f, 17f)
            }
        }
    }

    val FolderInput: ImageVector by lazy {
        lucideIcon("FolderInput") { s, w ->
            strokePath(s, w) {
                moveTo(2f, 9f); verticalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 4f, 3f)
                horizontalLineTo(7.9f)
                arcTo(2f, 2f, 0f, false, true, 9.6f, 3.9f)
                lineTo(10.5f, 5.1f)
                arcTo(2f, 2f, 0f, false, false, 12.1f, 6f)
                horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 22f, 8f)
                verticalLineTo(18f)
                arcTo(2f, 2f, 0f, false, true, 20f, 20f)
                horizontalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 2f, 18f)
                verticalLineTo(17f)
                moveTo(2f, 13f); horizontalLineTo(12f)
                moveTo(9f, 16f); lineTo(12f, 13f); lineTo(9f, 10f)
            }
        }
    }

    val FileOutput: ImageVector by lazy {
        lucideIcon("FileOutput") { s, w ->
            strokePath(s, w) {
                moveTo(4f, 22f); horizontalLineTo(18f)
                arcTo(2f, 2f, 0f, false, false, 20f, 20f)
                verticalLineTo(7f)
                lineTo(15f, 2f)
                horizontalLineTo(6f)
                arcTo(2f, 2f, 0f, false, false, 4f, 4f)
                verticalLineTo(8f)
                moveTo(14f, 2f); verticalLineTo(7f); horizontalLineTo(19f)
                moveTo(2f, 15f); horizontalLineTo(12f)
                moveTo(5f, 12f); lineTo(2f, 15f); lineTo(5f, 18f)
            }
        }
    }

    val Plus: ImageVector by lazy {
        lucideIcon("Plus") { s, w ->
            strokePath(s, w) {
                moveTo(5f, 12f); horizontalLineTo(19f)
                moveTo(12f, 5f); verticalLineTo(19f)
            }
        }
    }

    val Trash2: ImageVector by lazy {
        lucideIcon("Trash2") { s, w ->
            strokePath(s, w) {
                moveTo(3f, 6f); horizontalLineTo(21f)
                moveTo(19f, 6f); verticalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 17f, 22f)
                horizontalLineTo(7f)
                arcTo(2f, 2f, 0f, false, true, 5f, 20f)
                verticalLineTo(6f)
                moveTo(8f, 6f); verticalLineTo(4f)
                arcTo(2f, 2f, 0f, false, true, 10f, 2f)
                horizontalLineTo(14f)
                arcTo(2f, 2f, 0f, false, true, 16f, 4f)
                verticalLineTo(6f)
                moveTo(10f, 11f); verticalLineTo(17f)
                moveTo(14f, 11f); verticalLineTo(17f)
            }
        }
    }

    val Save: ImageVector by lazy {
        lucideIcon("Save") { s, w ->
            strokePath(s, w) {
                moveTo(15.2f, 3f)
                arcTo(2f, 2f, 0f, false, true, 16.6f, 3.6f)
                lineTo(20.4f, 7.4f)
                arcTo(2f, 2f, 0f, false, true, 21f, 8.8f)
                verticalLineTo(19f)
                arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                horizontalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 3f, 19f)
                verticalLineTo(5f)
                arcTo(2f, 2f, 0f, false, true, 5f, 3f)
                close()
                moveTo(17f, 21f); verticalLineTo(13f); horizontalLineTo(7f); verticalLineTo(21f)
                moveTo(7f, 3f); verticalLineTo(8f); horizontalLineTo(15f)
            }
        }
    }

    val Copy: ImageVector by lazy {
        lucideIcon("Copy") { s, w ->
            strokePath(s, w) {
                // Back/main rounded rect (8,8) -> (22,22)
                moveTo(10f, 8f); horizontalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 22f, 10f)
                verticalLineTo(20f)
                arcTo(2f, 2f, 0f, false, true, 20f, 22f)
                horizontalLineTo(10f)
                arcTo(2f, 2f, 0f, false, true, 8f, 20f)
                verticalLineTo(10f)
                arcTo(2f, 2f, 0f, false, true, 10f, 8f)
                close()
                moveTo(4f, 16f)
                curveTo(2.9f, 16f, 2f, 15.1f, 2f, 14f)
                verticalLineTo(4f)
                curveTo(2f, 2.9f, 2.9f, 2f, 4f, 2f)
                horizontalLineTo(14f)
                curveTo(15.1f, 2f, 16f, 2.9f, 16f, 4f)
            }
        }
    }

    val Check: ImageVector by lazy {
        lucideIcon("Check") { s, w ->
            strokePath(s, w) {
                moveTo(20f, 6f); lineTo(9f, 17f); lineTo(4f, 12f)
            }
        }
    }

    val Pencil: ImageVector by lazy {
        lucideIcon("Pencil") { s, w ->
            strokePath(s, w) {
                moveTo(21.17f, 6.81f)
                arcTo(1f, 1f, 0f, false, false, 17.19f, 2.83f)
                lineTo(3.84f, 16.17f)
                lineTo(2f, 22f)
                lineTo(7.83f, 20.16f)
                close()
                moveTo(15f, 5f); lineTo(19f, 9f)
            }
        }
    }

    val Sparkles: ImageVector by lazy {
        lucideIcon("Sparkles") { s, w ->
            strokePath(s, w) {
                moveTo(9.94f, 15.5f)
                lineTo(12f, 21f)
                lineTo(14.06f, 15.5f)
                lineTo(19.5f, 13.5f)
                lineTo(14.06f, 11.44f)
                lineTo(12f, 6f)
                lineTo(9.94f, 11.44f)
                lineTo(4.5f, 13.5f)
                close()
                moveTo(19f, 3f); verticalLineTo(7f)
                moveTo(21f, 5f); horizontalLineTo(17f)
                moveTo(5f, 3f); verticalLineTo(5f)
                moveTo(6f, 4f); horizontalLineTo(4f)
            }
        }
    }

    val Volume2: ImageVector by lazy {
        lucideIcon("Volume2") { s, w ->
            strokePath(s, w) {
                moveTo(11f, 4.7f); lineTo(6f, 9f); horizontalLineTo(3f)
                verticalLineTo(15f); horizontalLineTo(6f); lineTo(11f, 19.3f); close()
                moveTo(15.54f, 8.46f); arcTo(5f, 5f, 0f, false, true, 15.54f, 15.54f)
                moveTo(19.07f, 4.93f); arcTo(10f, 10f, 0f, false, true, 19.07f, 19.07f)
            }
        }
    }

    val VolumeX: ImageVector by lazy {
        lucideIcon("VolumeX") { s, w ->
            strokePath(s, w) {
                moveTo(11f, 4.7f); lineTo(6f, 9f); horizontalLineTo(3f)
                verticalLineTo(15f); horizontalLineTo(6f); lineTo(11f, 19.3f); close()
                moveTo(22f, 9f); lineTo(16f, 15f)
                moveTo(16f, 9f); lineTo(22f, 15f)
            }
        }
    }

    val RotateCcw: ImageVector by lazy {
        lucideIcon("RotateCcw") { s, w ->
            strokePath(s, w) {
                moveTo(3f, 12f)
                arcTo(9f, 9f, 0f, true, false, 12f, 3f)
                arcTo(9.75f, 9.75f, 0f, false, false, 5.26f, 5.74f)
                lineTo(3f, 8f)
                moveTo(3f, 3f); verticalLineTo(8f); horizontalLineTo(8f)
            }
        }
    }

    val ChevronDown: ImageVector by lazy {
        lucideIcon("ChevronDown") { s, w ->
            strokePath(s, w) {
                moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f)
            }
        }
    }

    val ChevronUp: ImageVector by lazy {
        lucideIcon("ChevronUp") { s, w ->
            strokePath(s, w) {
                moveTo(18f, 15f); lineTo(12f, 9f); lineTo(6f, 15f)
            }
        }
    }

    val X: ImageVector by lazy {
        lucideIcon("X") { s, w ->
            strokePath(s, w) {
                moveTo(18f, 6f); lineTo(6f, 18f)
                moveTo(6f, 6f); lineTo(18f, 18f)
            }
        }
    }

    val Palette: ImageVector by lazy {
        lucideIcon("Palette") { s, w ->
            strokePath(s, w) {
                moveTo(12f, 22f)
                curveTo(6.48f, 22f, 2f, 17.52f, 2f, 12f)
                curveTo(2f, 6.48f, 6.48f, 2f, 12f, 2f)
                curveTo(17.52f, 2f, 22f, 6.03f, 22f, 11f)
                curveTo(22f, 13.76f, 20.1f, 15f, 18f, 15f)
                horizontalLineTo(15.5f)
                curveTo(14.12f, 15f, 13.5f, 16.2f, 14.3f, 17.3f)
                curveTo(14.8f, 18.0f, 15f, 18.9f, 14.6f, 19.9f)
                curveTo(14.2f, 21.1f, 13.1f, 22f, 12f, 22f)
                close()
                moveTo(7.5f, 11.5f); lineTo(7.55f, 11.5f)
                moveTo(10.5f, 7.5f); lineTo(10.55f, 7.5f)
                moveTo(15.5f, 7.5f); lineTo(15.55f, 7.5f)
                moveTo(17.5f, 11.5f); lineTo(17.55f, 11.5f)
            }
        }
    }

    val Sun: ImageVector by lazy {
        lucideIcon("Sun") { s, w ->
            strokePath(s, w) {
                moveTo(12f, 8f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 16f)
                arcTo(4f, 4f, 0f, isMoreThanHalf = true, isPositiveArc = false, 12f, 8f)
                moveTo(12f, 2f); lineTo(12f, 4f)
                moveTo(12f, 20f); lineTo(12f, 22f)
                moveTo(4.93f, 4.93f); lineTo(6.34f, 6.34f)
                moveTo(17.66f, 17.66f); lineTo(19.07f, 19.07f)
                moveTo(2f, 12f); lineTo(4f, 12f)
                moveTo(20f, 12f); lineTo(22f, 12f)
                moveTo(6.34f, 17.66f); lineTo(4.93f, 19.07f)
                moveTo(19.07f, 4.93f); lineTo(17.66f, 6.34f)
            }
        }
    }

    val Moon: ImageVector by lazy {
        lucideIcon("Moon") { s, w ->
            strokePath(s, w) {
                moveTo(12f, 3f)
                arcTo(9f, 9f, 0f, isMoreThanHalf = true, isPositiveArc = false, 21f, 12f)
                curveTo(17.5f, 12f, 12f, 8.5f, 12f, 3f)
                close()
            }
        }
    }

    val Droplet: ImageVector by lazy {
        lucideIcon("Droplet") { s, w ->
            strokePath(s, w) {
                moveTo(12f, 2.69f)
                lineTo(17.66f, 8.35f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, 6.34f, 8.35f)
                close()
            }
        }
    }

    val MoreVertical: ImageVector by lazy {
        lucideIcon("MoreVertical") { s, _ ->
            strokePath(s, 3.2f) {
                moveTo(12f, 5f)
                lineTo(12f, 5.01f)
                moveTo(12f, 12f)
                lineTo(12f, 12.01f)
                moveTo(12f, 19f)
                lineTo(12f, 19.01f)
            }
        }
    }

    val Search: ImageVector by lazy {
        lucideIcon("Search") { s, w ->
            strokePath(s, w) {
                moveTo(19f, 11f)
                arcTo(8f, 8f, 0f, true, true, 3f, 11f)
                arcTo(8f, 8f, 0f, true, true, 19f, 11f)
                close()
                moveTo(21f, 21f)
                lineTo(16.65f, 16.65f)
            }
        }
    }

    val ShieldOff: ImageVector by lazy {
        lucideIcon("ShieldOff") { s, w ->
            strokePath(s, w) {
                moveTo(2f, 2f)
                lineTo(22f, 22f)
                moveTo(5f, 5f)
                arcTo(1f, 1f, 0f, false, false, 4f, 6f)
                verticalLineTo(13f)
                curveTo(4f, 18f, 7.5f, 20.5f, 11.66f, 21.95f)
                curveTo(11.88f, 22.02f, 12.12f, 22.02f, 12.34f, 21.95f)
                curveTo(14.3f, 21.26f, 16.12f, 20.17f, 17.57f, 18.74f)
                moveTo(19.65f, 14f)
                curveTo(19.88f, 13.68f, 20f, 13.34f, 20f, 13f)
                verticalLineTo(6f)
                arcTo(1f, 1f, 0f, false, false, 19f, 5f)
                curveTo(17f, 5f, 14.51f, 3.81f, 12.76f, 2.28f)
                arcTo(1.17f, 1.17f, 0f, false, false, 11.24f, 2.28f)
                curveTo(10.6f, 2.84f, 9.86f, 3.35f, 9.06f, 3.77f)
            }
        }
    }
}
