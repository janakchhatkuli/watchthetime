package com.watchthetime.brand

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AvTimer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sports
import androidx.compose.material.icons.rounded.SportsBasketball
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Timelapse
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** App icon set: Material Symbols (Rounded) plus a custom jersey. `Icon` tints them at the call site. */
object WttIcons {
    val Whistle: ImageVector get() = Icons.Rounded.Sports
    val Basketball: ImageVector get() = Icons.Rounded.SportsBasketball
    val Flag: ImageVector get() = Icons.Rounded.Flag
    val Stopwatch: ImageVector get() = Icons.Rounded.Timer
    val ShotClock: ImageVector get() = Icons.Rounded.AvTimer
    val Clock: ImageVector get() = Icons.Rounded.Schedule
    val Periods: ImageVector get() = Icons.Rounded.Timelapse
    val Timeout: ImageVector get() = Icons.Rounded.PauseCircle
    val Rules: ImageVector get() = Icons.Rounded.Gavel

    val Undo: ImageVector get() = Icons.AutoMirrored.Rounded.Undo
    val Redo: ImageVector get() = Icons.AutoMirrored.Rounded.Redo
    val Reset: ImageVector get() = Icons.Rounded.RestartAlt
    val Play: ImageVector get() = Icons.Rounded.PlayArrow
    val Pause: ImageVector get() = Icons.Rounded.Pause
    val Stop: ImageVector get() = Icons.Rounded.Stop
    val SkipNext: ImageVector get() = Icons.Rounded.SkipNext
    val SkipPrevious: ImageVector get() = Icons.Rounded.SkipPrevious

    val Plus: ImageVector get() = Icons.Rounded.Add
    val Minus: ImageVector get() = Icons.Rounded.Remove
    val Check: ImageVector get() = Icons.Rounded.Check
    val Close: ImageVector get() = Icons.Rounded.Close
    val Back: ImageVector get() = Icons.AutoMirrored.Rounded.ArrowBack
    val Forward: ImageVector get() = Icons.AutoMirrored.Rounded.ArrowForward
    val ChevronRight: ImageVector get() = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val Menu: ImageVector get() = Icons.Rounded.Menu
    val More: ImageVector get() = Icons.Rounded.MoreHoriz
    val Exit: ImageVector get() = Icons.AutoMirrored.Rounded.Logout

    val Pencil: ImageVector get() = Icons.Rounded.Edit
    val Trash: ImageVector get() = Icons.Rounded.DeleteOutline
    val Restore: ImageVector get() = Icons.Rounded.RestoreFromTrash
    val Save: ImageVector get() = Icons.Rounded.Save
    val Share: ImageVector get() = Icons.Rounded.Share
    val Import: ImageVector get() = Icons.Rounded.FileDownload
    val Csv: ImageVector get() = Icons.Rounded.GridOn
    val Pdf: ImageVector get() = Icons.Rounded.PictureAsPdf

    val Log: ImageVector get() = Icons.AutoMirrored.Rounded.FormatListBulleted
    val Table: ImageVector get() = Icons.Rounded.TableChart
    val Sliders: ImageVector get() = Icons.Rounded.Tune
    val Teams: ImageVector get() = Icons.Rounded.Groups
    val PersonAdd: ImageVector get() = Icons.Rounded.PersonAdd
    val Swap: ImageVector get() = Icons.Rounded.SwapHoriz
    val Home: ImageVector get() = Icons.Rounded.Home
    val Away: ImageVector get() = Icons.Rounded.DirectionsBus
    val Palette: ImageVector get() = Icons.Rounded.Palette

    val Speaker: ImageVector get() = Icons.AutoMirrored.Rounded.VolumeUp
    val Vibration: ImageVector get() = Icons.Rounded.Vibration
    val Bell: ImageVector get() = Icons.Rounded.NotificationsActive
    val Eye: ImageVector get() = Icons.Rounded.Visibility
    val Bulb: ImageVector get() = Icons.Rounded.Lightbulb
    val Hash: ImageVector get() = Icons.Rounded.Tag
    val Touch: ImageVector get() = Icons.Rounded.TouchApp
    val Shield: ImageVector get() = Icons.Rounded.Shield
    val LockOpen: ImageVector get() = Icons.Rounded.LockOpen
    val Rotate: ImageVector get() = Icons.Rounded.ScreenRotation

    /** Team jersey outline (no Material equivalent). */
    val Jersey: ImageVector = ImageVector.Builder("Jersey", 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 3f); lineTo(5f, 4f); lineTo(5f, 9f); lineTo(7f, 10f); lineTo(7f, 21f); lineTo(17f, 21f)
            lineTo(17f, 10f); lineTo(19f, 9f); lineTo(19f, 4f); lineTo(16f, 3f)
            arcTo(4f, 4f, 0f, false, true, 8f, 3f)
        }
    }.build()
}
