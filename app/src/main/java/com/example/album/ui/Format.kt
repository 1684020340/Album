package com.example.album.ui

import com.example.album.data.MediaItem
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val zh = Locale.CHINA

fun LocalDate.fmtDay(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> format(DateTimeFormatter.ofPattern(if (year == today.year) "M月d日 EEEE" else "yyyy年M月d日 EEEE", zh))
    }
}

fun LocalDate.fmtFull(): String = format(DateTimeFormatter.ofPattern("yyyy年M月d日", zh))

fun Long.fmtDuration(): String {
    val s = this / 1000
    return "%d:%02d".format(s / 60, s % 60)
}

fun MediaItem.timeText(): String =
    Instant.ofEpochMilli(takenMillis).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("HH:mm", zh))

fun MediaItem.dateTitle(): String = date.fmtDay()
