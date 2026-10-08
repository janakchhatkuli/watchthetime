package com.watchthetime.brand

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Custom-drawn icon set (24×24 grid, 2px square-capped strokes, mitred joins). Drawn in black;
 * `Icon` tints them, so they pick up whatever colour the call site uses.
 */
object WttIcons {

    private fun icon(name: String, block: Builder.() -> Unit): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        Builder(b).block()
        return b.build()
    }

    class Builder(private val b: ImageVector.Builder) {
        fun stroke(width: Float = 2f, d: PathBuilder.() -> Unit) {
            b.path(
                stroke = SolidColor(Color.Black), strokeLineWidth = width,
                strokeLineCap = StrokeCap.Square, strokeLineJoin = StrokeJoin.Miter, pathBuilder = d,
            )
        }

        fun fill(d: PathBuilder.() -> Unit) {
            b.path(fill = SolidColor(Color.Black), pathBuilder = d)
        }
    }

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, true, true, cx + r, cy)
        arcTo(r, r, 0f, true, true, cx - r, cy)
        close()
    }

    /** Referee whistle: barrel with mouthpiece and pea hole. */
    val Whistle: ImageVector = icon("Whistle") {
        stroke {
            moveTo(3f, 9f); lineTo(14f, 9f)
            moveTo(14f, 9f); lineTo(14f, 6f); lineTo(21f, 6f); lineTo(21f, 10f); lineTo(16.5f, 10f)
            moveTo(3f, 9f); lineTo(3f, 12f)
        }
        stroke { circle(9f, 14f, 5f) }
        fill { circle(9f, 14f, 1.5f) }
    }

    /** Foul flag (technical / flagrant badge). */
    val Flag: ImageVector = icon("Flag") {
        stroke {
            moveTo(5f, 21f); lineTo(5f, 3f)
            moveTo(5f, 4f); lineTo(19f, 4f); lineTo(15f, 8.5f); lineTo(19f, 13f); lineTo(5f, 13f)
        }
    }

    val Stopwatch: ImageVector = icon("Stopwatch") {
        stroke { circle(12f, 14f, 7f) }
        stroke {
            moveTo(10f, 3f); lineTo(14f, 3f)
            moveTo(12f, 3f); lineTo(12f, 7f)
            moveTo(12f, 14f); lineTo(12f, 10f)
            moveTo(18f, 7f); lineTo(19.5f, 5.5f)
        }
    }

    val Undo: ImageVector = icon("Undo") {
        stroke {
            moveTo(4f, 10f); lineTo(15f, 10f)
            arcTo(5f, 5f, 0f, false, true, 15f, 20f)
            lineTo(9f, 20f)
            moveTo(8f, 6f); lineTo(4f, 10f); lineTo(8f, 14f)
        }
    }

    val Redo: ImageVector = icon("Redo") {
        stroke {
            moveTo(20f, 10f); lineTo(9f, 10f)
            arcTo(5f, 5f, 0f, false, false, 9f, 20f)
            lineTo(15f, 20f)
            moveTo(16f, 6f); lineTo(20f, 10f); lineTo(16f, 14f)
        }
    }

    val Pencil: ImageVector = icon("Pencil") {
        stroke {
            moveTo(4f, 20f); lineTo(4f, 16f); lineTo(15f, 5f); lineTo(19f, 9f); lineTo(8f, 20f); close()
            moveTo(12.5f, 7.5f); lineTo(16.5f, 11.5f)
        }
    }

    val Play: ImageVector = icon("Play") { fill { moveTo(7f, 4f); lineTo(20f, 12f); lineTo(7f, 20f); close() } }

    val Pause: ImageVector = icon("Pause") {
        fill { moveTo(6f, 4f); lineTo(10f, 4f); lineTo(10f, 20f); lineTo(6f, 20f); close() }
        fill { moveTo(14f, 4f); lineTo(18f, 4f); lineTo(18f, 20f); lineTo(14f, 20f); close() }
    }

    val Plus: ImageVector = icon("Plus") { stroke { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) } }
    val Minus: ImageVector = icon("Minus") { stroke { moveTo(5f, 12f); lineTo(19f, 12f) } }

    val Back: ImageVector = icon("Back") {
        stroke { moveTo(20f, 12f); lineTo(5f, 12f); moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f) }
    }

    val Close: ImageVector = icon("Close") { stroke { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }

    val Check: ImageVector = icon("Check") { stroke { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) } }

    val ChevronRight: ImageVector = icon("ChevronRight") { stroke { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) } }

    val Menu: ImageVector = icon("Menu") {
        stroke { moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f) }
    }

    /** Event log: ruled list with time ticks. */
    val Log: ImageVector = icon("Log") {
        stroke {
            moveTo(4f, 5f); lineTo(6f, 5f); moveTo(9f, 5f); lineTo(20f, 5f)
            moveTo(4f, 12f); lineTo(6f, 12f); moveTo(9f, 12f); lineTo(20f, 12f)
            moveTo(4f, 19f); lineTo(6f, 19f); moveTo(9f, 19f); lineTo(20f, 19f)
        }
    }

    /** Box score grid. */
    val Table: ImageVector = icon("Table") {
        stroke {
            moveTo(3f, 4f); lineTo(21f, 4f); lineTo(21f, 20f); lineTo(3f, 20f); close()
            moveTo(3f, 9f); lineTo(21f, 9f); moveTo(3f, 14.5f); lineTo(21f, 14.5f); moveTo(9f, 4f); lineTo(9f, 20f)
        }
    }

    val Share: ImageVector = icon("Share") {
        stroke {
            moveTo(12f, 15f); lineTo(12f, 3f); moveTo(7f, 8f); lineTo(12f, 3f); lineTo(17f, 8f)
            moveTo(5f, 12f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 12f)
        }
    }

    /** Settings: three sliders (avoids the generic gear). */
    val Sliders: ImageVector = icon("Sliders") {
        stroke {
            moveTo(4f, 6f); lineTo(20f, 6f); moveTo(4f, 12f); lineTo(20f, 12f); moveTo(4f, 18f); lineTo(20f, 18f)
        }
        fill { moveTo(7f, 3.5f); lineTo(11f, 3.5f); lineTo(11f, 8.5f); lineTo(7f, 8.5f); close() }
        fill { moveTo(14f, 9.5f); lineTo(18f, 9.5f); lineTo(18f, 14.5f); lineTo(14f, 14.5f); close() }
        fill { moveTo(5f, 15.5f); lineTo(9f, 15.5f); lineTo(9f, 20.5f); lineTo(5f, 20.5f); close() }
    }

    /** Team: jersey outline. */
    val Jersey: ImageVector = icon("Jersey") {
        stroke {
            moveTo(8f, 3f); lineTo(5f, 4f); lineTo(5f, 9f); lineTo(7f, 10f); lineTo(7f, 21f); lineTo(17f, 21f)
            lineTo(17f, 10f); lineTo(19f, 9f); lineTo(19f, 4f); lineTo(16f, 3f)
            arcTo(4f, 4f, 0f, false, true, 8f, 3f)
        }
    }

    val Trash: ImageVector = icon("Trash") {
        stroke {
            moveTo(4f, 6f); lineTo(20f, 6f); moveTo(9f, 6f); lineTo(9f, 3f); lineTo(15f, 3f); lineTo(15f, 6f)
            moveTo(6f, 6f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 6f)
            moveTo(10f, 10f); lineTo(10f, 17f); moveTo(14f, 10f); lineTo(14f, 17f)
        }
    }

    val Restore: ImageVector = icon("Restore") {
        stroke {
            moveTo(4f, 12f)
            arcTo(8f, 8f, 0f, true, true, 6.5f, 17.8f)
            moveTo(1f, 9f); lineTo(4f, 12f); lineTo(7f, 9f)
            moveTo(12f, 8f); lineTo(12f, 12f); lineTo(15f, 14f)
        }
    }

    /** Timeout: "T" inside a hand-stop square. */
    val Timeout: ImageVector = icon("Timeout") {
        stroke { moveTo(4f, 5f); lineTo(20f, 5f); moveTo(12f, 5f); lineTo(12f, 20f) }
    }

    val Import: ImageVector = icon("Import") {
        stroke {
            moveTo(12f, 3f); lineTo(12f, 15f); moveTo(7f, 10f); lineTo(12f, 15f); lineTo(17f, 10f)
            moveTo(5f, 19f); lineTo(5f, 21f); lineTo(19f, 21f); lineTo(19f, 19f)
        }
    }

    /** Shot clock reset: circular arrow. */
    val Reset: ImageVector = icon("Reset") {
        stroke {
            moveTo(20f, 12f)
            arcTo(8f, 8f, 0f, true, true, 17.6f, 6.3f)
            moveTo(18f, 2f); lineTo(18f, 6.5f); lineTo(13.5f, 6.5f)
        }
    }

    val Speaker: ImageVector = icon("Speaker") {
        stroke {
            moveTo(3f, 9f); lineTo(7f, 9f); lineTo(12f, 4f); lineTo(12f, 20f); lineTo(7f, 15f); lineTo(3f, 15f); close()
            moveTo(16f, 9f); lineTo(16f, 15f); moveTo(19.5f, 6f); lineTo(19.5f, 18f)
        }
    }
}
