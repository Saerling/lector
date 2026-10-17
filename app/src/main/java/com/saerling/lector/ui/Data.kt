package com.saerling.lector.ui

import androidx.compose.ui.graphics.Color

/** Modelo de ejemplo. Cuando quieras, reemplázalo por tu modelo real de series/CBZ. */
data class Series(val id: String, val title: String, val subtitle: String, val color: Color)

object SampleData {
    val series = listOf(
        Series("1", "Berserk", "Kentaro Miura", Color(0xFF7A1F1F)),
        Series("2", "Vagabond", "Takehiko Inoue", Color(0xFF1F4A7A)),
        Series("3", "Monster", "Naoki Urasawa", Color(0xFF2E6B4F)),
        Series("4", "Akira", "Katsuhiro Otomo", Color(0xFF7A5A1F)),
        Series("5", "Blame!", "Tsutomu Nihei", Color(0xFF4A2E6B)),
        Series("6", "Uzumaki", "Junji Ito", Color(0xFF5A5A5A))
    )
}
