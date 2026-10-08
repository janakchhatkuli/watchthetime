package com.watchthetime.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.pow

@Serializable
enum class TeamSide {
    HOME, AWAY;

    val other: TeamSide get() = if (this == HOME) AWAY else HOME
    val tag: String get() = if (this == HOME) "H" else "A"
}

/**
 * Foul categories. PERSONAL is the default; the others are "flags" that are rendered as
 * visible badges on the player (T / F / U) in addition to being counted.
 */
@Serializable
enum class FoulType(val code: String, val label: String) {
    PERSONAL("P", "Personal"),
    TECHNICAL("T", "Technical"),
    FLAGRANT("F", "Flagrant"),
    UNSPORTSMANLIKE("U", "Unsportsmanlike");

    val isFlag: Boolean get() = this != PERSONAL
}

@Serializable
data class TeamInfo(
    val name: String,
    val shortName: String,
    /** ARGB colour from [TeamColors]. Always rendered as a solid block, never as text colour. */
    val colorArgb: Long,
) {
    companion object {
        fun default(side: TeamSide) = when (side) {
            TeamSide.HOME -> TeamInfo("Home", "HOME", TeamColors.OFF_WHITE.argb)
            TeamSide.AWAY -> TeamInfo("Away", "AWAY", TeamColors.AMBER.argb)
        }
    }
}

@Serializable
data class PlayerInfo(
    val id: String,
    val side: TeamSide,
    /** Jersey number as text so "0" and "00" are distinct. */
    val number: String,
    val name: String = "",
) {
    val label: String get() = if (name.isBlank()) "#$number" else "#$number ${name.trim()}"
}

/** Fixed, flat jersey palette so team colours always stay on-brand (no arbitrary hues). */
enum class TeamColors(val argb: Long, val label: String) {
    OFF_WHITE(0xFFEFEBE3, "White"),
    AMBER(0xFFFF8A00, "Amber"),
    RED(0xFFD7262B, "Red"),
    BLACK(0xFF1A1A1A, "Black"),
    ROYAL(0xFF1F4AA8, "Royal"),
    NAVY(0xFF14213D, "Navy"),
    GREEN(0xFF1E7B3A, "Green"),
    GOLD(0xFFE5B700, "Gold"),
    MAROON(0xFF6E1E2B, "Maroon"),
    TEAL(0xFF0F7C80, "Teal"),
    GREY(0xFF8A8A85, "Grey");

    companion object {
        fun fromArgb(argb: Long) = entries.firstOrNull { it.argb == argb }

        /** True when black text is more legible than off-white on this colour. */
        fun prefersDarkText(argb: Long): Boolean {
            val r = ((argb shr 16) and 0xFF) / 255.0
            val g = ((argb shr 8) and 0xFF) / 255.0
            val b = (argb and 0xFF) / 255.0
            fun lin(c: Double) = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            val l = 0.2126 * lin(r) + 0.7152 * lin(g) + 0.0722 * lin(b)
            return l > 0.30
        }
    }
}
