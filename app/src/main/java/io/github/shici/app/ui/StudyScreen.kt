package io.github.shici.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.shici.core.*

@Composable fun StudyScreen(state: AppState, speak: (String) -> Unit, back: () -> Unit,
                           reveal: () -> Unit, answer: (Rating) -> Unit, undo: () -> Unit = {}, next: () -> Unit = {}, other: () -> Unit = {}) {
    val session = state.session ?: return
    val progress = session.progress
    val waiting = session.current?.availableAt?.isAfter(state.now) == true
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (session.mode == SessionMode.LEARN) "学习" else "复习", back) {
            if (progress.lastActionId != null) IconButton(undo, enabled = !session.saving) {
                Icon(Icons.AutoMirrored.Outlined.Undo, "撤销上一条")
            }
            Text("${progress.completed} / ${progress.total}", Modifier.padding(end = 20.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(progress = { progress.completed.toFloat() / progress.total.coerceAtLeast(1) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp))
        when {
            session.finished -> StudyResult(state, back, next)
            waiting -> WaitingCard(state, back, other)
            else -> {
                val entry = session.entry ?: return
                StudyContent(state, entry, speak, Modifier.weight(1f))
                Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!session.revealed) {
                            AccentButton("显示答案", reveal, Modifier.fillMaxWidth(), !session.saving)
                            QuietText("先回忆，再确认", Modifier.align(Alignment.CenterHorizontally))
                        } else {
                            Text("刚才回想得怎么样？", style = MaterialTheme.typography.labelLarge)
                            RatingButtons(state, answer)
                            QuietText(if (session.mode == SessionMode.LEARN) "忘记不会消耗学习任务；下次到期再巩固。" else "按揭晓前的回忆表现选择，间隔由记忆记录计算。")
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun StudyContent(state: AppState, entry: WordEntry, speak: (String) -> Unit, modifier: Modifier) {
    val session = state.session!!
    val count = state.snapshot?.words?.find { it.word == entry.word }?.additionCount ?: 1
    val caption = "累计加入 $count 次 · ${if (session.mode == SessionMode.LEARN) "本组学习" else "到期复习"}"
    val scroll = rememberScrollState()
    LaunchedEffect(session.progress.answers, session.current) { scroll.scrollTo(0) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(0.42f).fillMaxHeight().verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    QuietText(caption)
                    Spacer(Modifier.height(16.dp))
                    WordHeading(entry, speak, centered = true)
                }
                Column(Modifier.weight(0.58f).fillMaxHeight().verticalScroll(scroll)) {
                    if (session.revealed) MeaningCard(entry) else RecallPrompt()
                }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                QuietText(caption)
                Spacer(Modifier.height(24.dp))
                WordHeading(entry, speak, centered = true)
                Spacer(Modifier.height(24.dp))
                if (!session.revealed) RecallPrompt()
                AnimatedVisibility(session.revealed, enter = fadeIn() + expandVertically()) { MeaningCard(entry) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable private fun RecallPrompt() {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("先在心里说出释义", style = MaterialTheme.typography.titleMedium)
            QuietText("想不起来也没关系，揭晓后如实选择。")
        }
    }
}

@Composable private fun MeaningCard(entry: WordEntry) {
    var expanded by rememberSaveable(entry.word) { mutableStateOf(false) }
    val senses = entry.learningSenses()
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.senseLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            (if (expanded) senses else senses.take(3)).forEach { Text(it.text, style = MaterialTheme.typography.bodyLarge) }
            if (senses.size > 3) TextButton({ expanded = !expanded }) { Text(if (expanded) "收起释义" else "展开其余 ${senses.size - 3} 条释义") }
            if (entry.editorialSenses.isNotEmpty()) QuietText("编辑整理 · 非频率统计")
            if (entry.example.isNotBlank()) {
                HorizontalDivider()
                Text(entry.example, style = MaterialTheme.typography.bodyLarge)
                Text(entry.exampleTranslation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                QuietText("原创用法例句 · 非真题")
            }
        }
    }
}

@Composable private fun RatingButtons(state: AppState, answer: (Rating) -> Unit) {
    val session = state.session ?: return
    val memory = state.snapshot?.words?.find { it.word == session.current?.word }?.memory
    val intervals = Rating.entries.associateWith { rating -> runCatching {
        intervalLabel(state.now, FsrsScheduler(state.retention).review(memory, rating, state.now).dueAt)
    }.getOrNull() }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 560.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Rating.entries.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { rating ->
                        val description = when (rating) {
                            Rating.AGAIN -> "没想起"; Rating.HARD -> "费力想起"; Rating.GOOD -> "正确想起"; Rating.EASY -> "立刻想起"
                        }
                        val colors = if (rating == Rating.GOOD) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                        FilledTonalButton({ answer(rating) }, modifier = Modifier.weight(1f).heightIn(min = 62.dp),
                            enabled = !session.saving && intervals[rating] != null, shape = RoundedCornerShape(16.dp), colors = colors,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(rating.label, style = MaterialTheme.typography.labelLarge)
                                Text("$description · ${intervals[rating] ?: "检查时间"}", style = MaterialTheme.typography.labelSmall,
                                    textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun WaitingCard(state: AppState, back: () -> Unit, other: () -> Unit) {
    val progress = state.session!!.progress
    val due = progress.items.first().availableAt
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Outlined.Schedule, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("给记忆一点间隔", style = MaterialTheme.typography.headlineMedium)
        Text("还有 ${progress.items.size} 个词需要再巩固。\n最早可在${dueLabel(state.now, due)}继续。",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        QuietText("未记住的学习任务仍然保留，离开页面也不会丢失。")
        if ((state.snapshot?.readyLearningWords ?: 0) > 0) {
            AccentButton("先学其他词", other, Modifier.fillMaxWidth())
            QuietText("另开一组可学词，当前等待任务会留到后续组。")
        }
        AccentButton("稍后继续", back, Modifier.fillMaxWidth())
    }
}

@Composable private fun StudyResult(state: AppState, back: () -> Unit, next: () -> Unit) {
    val progress = state.session!!.progress
    val more = if (progress.mode == SessionMode.LEARN) (state.snapshot?.pendingCount ?: 0) > 0 else state.dueWords.isNotEmpty()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(24.dp))
        Icon(Icons.Outlined.CheckCircleOutline, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("这一组，完成了", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text("已保存 ${progress.completed} 次${if (progress.mode == SessionMode.LEARN) "学习" else "复习"}", style = MaterialTheme.typography.titleMedium)
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(22.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                SmallMetric("正确回忆", "${progress.answers - progress.forgotten} 次")
                SmallMetric("未能回忆", "${progress.forgotten} 次")
            }
        }
        Text(if (progress.forgotten > 0) "遗忘是复习的一部分，需要巩固的词已重新安排。" else "下一次复习已按这次的回忆表现安排。",
            textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.nextReviewAt?.let { QuietText("下一次复习 · ${dueLabel(state.now, it)}") }
        if (more) AccentButton("继续下一组", next, Modifier.fillMaxWidth())
        OutlinedButton(back, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("先到这里") }
        QuietText("误点了评分？可用右上角撤销上一条。")
    }
}
