package com.watchthetime.brand

import androidx.compose.ui.graphics.Color

/** Arena scoreboard palette. Flat colours only: no gradients, glow or translucency effects. */
object Wtt {
    val Black = Color(0xFF0A0A0A)
    val OffWhite = Color(0xFFEFEBE3)
    val Amber = Color(0xFFFF8A00)
    val Red = Color(0xFFD7262B)

    /** Raised panels on the black background. */
    val Panel = Color(0xFF151515)
    val PanelHigh = Color(0xFF1F1F1F)
    /** 1dp rules between panels. */
    val Line = Color(0xFF2C2C2A)
    /** Unlit seven-segment backdrop. */
    val Ghost = Color(0xFF1C1A17)
    /** Secondary text. */
    val Muted = Color(0xFF8A8A85)
    val Disabled = Color(0xFF4A4A47)

    fun argb(v: Long) = Color(v.toInt())

    /** Text colour that is legible on top of a team colour block. */
    fun onTeam(argb: Long): Color = if (com.watchthetime.domain.model.TeamColors.prefersDarkText(argb)) Black else OffWhite
}
