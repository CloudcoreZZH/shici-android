package io.github.shici.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.SessionMode
import io.github.shici.core.WordBook
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun BookPicker(books: List<WordBook>, selected: WordBook?, choose: (Long) -> Unit, create: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    Box {
        TextButton({ expanded = true }) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(selected?.name ?: "考研生词本", maxLines = 1, modifier = Modifier.widthIn(max = 210.dp))
            Icon(Icons.Outlined.ExpandMore, "选择词书")
        }
        DropdownMenu(expanded, { expanded = false }) {
            books.forEach { book -> DropdownMenuItem(text = { Text(book.name) }, onClick = { choose(book.id); expanded = false }) }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("新建词书") }, onClick = { creating = true; expanded = false })
        }
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("新建词书") }, text = {
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("词书名称") }, singleLine = true)
    }, confirmButton = { TextButton({ create(name); name = ""; creating = false }, enabled = name.isNotBlank()) { Text("创建") } },
        dismissButton = { TextButton({ creating = false }) { Text("取消") } })
}


@Composable fun HomeScreen(state: AppState, chooseBook: (Long) -> Unit, createBook: (String) -> Unit,
                          learn: () -> Unit, review: () -> Unit, search: () -> Unit) {
    val pending = state.snapshot?.pendingCount ?: 0
    val due = state.dueWords.size
    val resume = state.savedSessions[SessionMode.LEARN]
    val waiting = resume?.items?.all { it.availableAt.isAfter(state.now) } == true
    val date = remember(state.now) { state.now.atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(2.dp).height(46.dp).background(MaterialTheme.colorScheme.primary))
                Text("拾词", Modifier.padding(start = 14.dp), fontSize = 42.sp, lineHeight = 52.sp)
            }
            QuietText(date)
        }
        BookPicker(state.books, state.snapshot?.book, chooseBook, createBook)
        HorizontalDivider()
        QuietText("一个词，更大的世界。")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TaskNumber("待学习", pending, learn, Modifier.weight(1f))
            VerticalDivider(Modifier.height(76.dp))
            TaskNumber("到期复习", due, review, Modifier.weight(1f).padding(start = 28.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(when { due > 0 -> "先巩固，再向前。"; waiting -> "给记忆一点间隔。"
                pending > 0 -> "把见过的词，记住。"; else -> "从一个生词开始。" }, style = MaterialTheme.typography.titleMedium)
            if (waiting) QuietText("${dueLabel(state.now, resume!!.items.minOf { it.availableAt })}可继续巩固，进度已保存。")
            else if (resume != null) QuietText("本组已完成 ${resume.completed} / ${resume.total}，接着上次继续。")
            AccentButton(when { due > 0 -> "开始复习  →"; waiting -> "查看学习安排"; resume != null -> "继续学习"
                pending > 0 -> "开始学习"; else -> "去查词" },
                when { due > 0 -> review; pending > 0 || resume != null -> learn; else -> search }, Modifier.fillMaxWidth())
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SmallMetric("今日已学", "${state.snapshot?.completedToday ?: 0}")
            SmallMetric("今日复习", "${state.snapshot?.reviewedToday ?: 0}")
            SmallMetric("词书收录", "${state.snapshot?.words?.size ?: 0}")
        }
        ReviewCalendar(state)
        state.nextReviewAt?.let { QuietText("下一次复习 · ${dueLabel(state.now, it)}") }
        QuietText("每次加入，都再学一次。")
        Spacer(Modifier.height(8.dp))
    }
}

@Composable internal fun ReviewCalendar(state: AppState) {
    val zone = ZoneId.systemDefault()
    val today = state.now.atZone(zone).toLocalDate()
    val scheduled = remember(state.snapshot?.words, today, zone) {
        state.snapshot?.words.orEmpty().filter { it.pendingCount == 0 && it.memory != null }
            .groupingBy { it.memory!!.dueAt.atZone(zone).toLocalDate().coerceAtLeast(today) }.eachCount()
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("复习日历", style = MaterialTheme.typography.titleMedium)
            QuietText("未来 7 天 · 预计")
        }
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val day = today.plusDays(index.toLong())
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietText(if (index == 0) "今天" else listOf("一", "二", "三", "四", "五", "六", "日")[day.dayOfWeek.value - 1])
                    Surface(shape = CircleShape, color = if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface) {
                        Box(Modifier.sizeIn(minWidth = 34.dp, minHeight = 34.dp), contentAlignment = Alignment.Center) {
                            Text(day.dayOfMonth.toString(), fontFamily = BookSerif,
                                color = if (index == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    QuietText("${scheduled[day] ?: 0}")
                }
            }
        }
        QuietText("数字为预计复习词数，随答题调整。")
    }
}

@Composable private fun TaskNumber(label: String, count: Int, click: () -> Unit, modifier: Modifier) {
    Column(modifier.clickable(onClickLabel = label, onClick = click).padding(vertical = 8.dp)) {
        Text(count.toString(), fontFamily = BookSerif, fontSize = 60.sp, lineHeight = 68.sp)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable internal fun SmallMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, fontFamily = BookSerif, fontSize = 28.sp)
        QuietText(label)
    }
}
