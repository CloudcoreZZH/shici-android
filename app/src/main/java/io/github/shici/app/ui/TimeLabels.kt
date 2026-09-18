package io.github.shici.app.ui

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

fun intervalLabel(from: Instant, to: Instant): String {
    val seconds = Duration.between(from, to).seconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "不足 1 分钟"
        seconds < 3600 -> "${ceil(seconds / 60.0).toInt()} 分钟"
        seconds < 86400 -> "${ceil(seconds / 3600.0).toInt()} 小时"
        else -> "${ceil(seconds / 86400.0).toInt()} 天"
    }
}

fun dueLabel(now: Instant, due: Instant): String {
    if (!due.isAfter(now)) return "现在可以复习"
    val zone = ZoneId.systemDefault()
    val day = due.atZone(zone).toLocalDate()
    val today = now.atZone(zone).toLocalDate()
    val time = due.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (day) {
        today -> "今天 $time"
        today.plusDays(1) -> "明天 $time"
        else -> due.atZone(zone).format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
    }
}
