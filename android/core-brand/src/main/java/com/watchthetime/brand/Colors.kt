package com.watchthetime.brand

import androidx.compose.ui.graphics.Color

/** Soft scoreboard palette: warm charcoal surfaces, toned-down amber and red. Flat colours only. */
object Wtt {
    val Black = Color(0xFF141312)
    val OffWhite = Color(0xFFEFEBE3)
    val Amber = Color(0xFFFFA23A)
    val Red = Color(0xFFE5534B)

    /** Raised panels on the background. */
    val Panel = Color(0xFF1E1C1A)
    val PanelHigh = Color(0xFF2B2826)
    /** 1dp rules between panels. */
    val Line = Color(0xFF383532)
    /** Unlit seven-segment backdrop. */
    val Ghost = Color(0xFF242120)
    /** Secondary text. */
    val Muted = Color(0xFF9C9891)
    val Disabled = Color(0xFF575450)

    fun argb(v: Long) = Color(v.toInt())

    /** Text colour that is legible on top of a team colour block. */
    fun onTeam(argb: Long): Color = if (com.watchthetime.domain.model.TeamColors.prefersDarkText(argb)) Black else OffWhite
}
