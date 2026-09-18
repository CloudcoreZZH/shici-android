package io.github.shici.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import io.github.shici.core.StudyDays

fun intervalLabel(from: Instant, to: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
    "${StudyDays.between(from, to, zone).coerceAtLeast(1)} 天"

fun dueLabel(now: Instant, due: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    if (!due.isAfter(now)) return "今天可以复习"
    val day = due.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    return when (day) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.plusDays(2) -> "后天"
        else -> due.atZone(zone).format(DateTimeFormatter.ofPattern(if (day.year == today.year) "M月d日" else "yyyy年M月d日"))
    }
}
