package io.github.shici.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
    val resumeLearn = state.savedSessions[SessionMode.LEARN]
    val learningWaits = resumeLearn?.items?.all { it.availableAt.isAfter(state.now) } == true
    val hasWords = state.snapshot?.words?.isNotEmpty() == true
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("拾词", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                QuietText(state.now.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("M月d日 · EEEE", Locale.CHINA)))
            }
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Text("离线 · 专注", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        BookPicker(state.books, state.snapshot?.book, chooseBook, createBook)
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (due > 0) "先巩固，再向前。" else if (pending > 0) "把见过的词，记住。" else "从一个生词开始。",
                    fontSize = 28.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(when {
                    due > 0 -> "有 $due 个词到了复习时间。反复加入的词会先出现。"
                    learningWaits -> "这一组正在等待巩固，${dueLabel(state.now, resumeLearn!!.items.minOf { it.availableAt })}可继续。已完成的记录都已保存。"
                    resumeLearn != null -> "上次已完成 ${resumeLearn.completed} / ${resumeLearn.total}，随时接着学。"
                    pending > 0 -> "还有 $pending 次学习任务。每组最多 ${state.groupSize} 个词，专注眼前这一组。"
                    hasWords -> "此刻没有新任务。到期时，复习会在这里等你。"
                    else -> "查到不熟悉的单词，放进词书。下一次遇见它，试着自己想起来。"
                }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                AccentButton(when {
                    due > 0 -> "开始复习"
                    learningWaits -> "查看学习安排"
                    resumeLearn != null -> "继续学习"
                    pending > 0 -> "开始学习"
                    else -> "去查词"
                }, when { due > 0 -> review; pending > 0 -> learn; else -> search }, Modifier.fillMaxWidth())
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TaskTile("待学习", pending, "次任务", learn, Modifier.weight(1f))
            TaskTile("到期复习", due, "个单词", review, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("今天的积累", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    SmallMetric("完成学习", "${state.snapshot?.completedToday ?: 0} 次")
                    SmallMetric("完成复习", "${state.snapshot?.reviewedToday ?: 0} 次")
                    SmallMetric("词书收录", "${state.snapshot?.words?.size ?: 0} 词")
                }
            }
            state.nextReviewAt?.let { QuietText("下一次复习 · ${dueLabel(state.now, it)}") }
            QuietText("每次加入都再学一次 · 同组不连续刷同一个词")
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable private fun TaskTile(title: String, count: Int, unit: String, click: () -> Unit, modifier: Modifier) {
    OutlinedCard(click, modifier = modifier, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(count.toString(), fontSize = 38.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                QuietText(unit)
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable internal fun SmallMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        QuietText(label)
    }
}
