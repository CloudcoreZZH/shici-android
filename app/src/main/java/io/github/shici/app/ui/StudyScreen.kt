package io.github.shici.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.*

private data class StudyPage(val entry: WordEntry, val key: String, val revealed: Boolean, val count: Int)

@Composable fun StudyScreen(state: AppState, speak: (String) -> Unit, back: () -> Unit,
                           reveal: () -> Unit, answer: (Rating) -> Unit, undo: () -> Unit = {}, next: () -> Unit = {}, other: () -> Unit = {}) {
    val session = state.session ?: return
    val progress = session.progress
    val waiting = session.current?.availableAt?.isAfter(state.now) == true
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (session.mode == SessionMode.LEARN) "学习" else "复习", back) {
            Text("${progress.completed} / ${progress.total}", Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.labelLarge, fontFamily = BookSerif)
            if (progress.lastActionId != null) IconButton(undo, enabled = !session.saving) {
                Icon(Icons.AutoMirrored.Outlined.Undo, "撤销上一条")
            } else Spacer(Modifier.width(8.dp))
        }
        val animatedProgress = animateFloatAsState(progress.completed.toFloat() / progress.total.coerceAtLeast(1),
            animationSpec = Motion.enter(), label = "study-progress")
        LinearProgressIndicator(progress = { animatedProgress.value },
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).height(2.dp))
        when {
            session.finished -> StudyResult(state, back, next)
            waiting -> WaitingCard(state, back, other)
            else -> {
                val entry = session.entry ?: return
                val count = remember(state.snapshot?.words, entry.word) {
                    state.snapshot?.words?.find { it.word == entry.word }?.additionCount ?: 1
                }
                val page = StudyPage(entry, session.current?.taskId ?: entry.word, session.revealed, count)
                AnimatedContent(page, modifier = Modifier.weight(1f), contentKey = { it.key },
                    transitionSpec = { (fadeIn(Motion.pageEnter()) togetherWith fadeOut(Motion.exit())).using(null) },
                    label = "study-word") { content -> StudyContent(content, speak) }
                StudyDock(state, reveal, answer)
            }
        }
    }
}

@Composable private fun StudyContent(page: StudyPage, speak: (String) -> Unit) {
    var expanded by rememberSaveable(page.key) { mutableStateOf(false) }
    val senses = remember(page.entry) { page.entry.learningSenses() }
    // Reveal changes content once; subsequent frames only change opacity, not size or composition.
    val revealAlpha = remember(page.key) { Animatable(if (page.revealed) 1f else 0f) }
    LaunchedEffect(page.revealed) {
        if (page.revealed) revealAlpha.animateTo(1f, Motion.enter()) else revealAlpha.snapTo(0f)
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 600.dp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                LazyColumn(Modifier.weight(0.42f).fillMaxHeight(), contentPadding = PaddingValues(vertical = 16.dp)) {
                    item { WordHeading(page.entry, speak); QuietText("累计加入 ${page.count} 次") }
                }
                LazyColumn(Modifier.weight(0.58f).fillMaxHeight(), contentPadding = PaddingValues(vertical = 12.dp)) {
                    studyAnswer(page, senses, expanded, { expanded = !expanded }, Modifier.graphicsLayer { alpha = revealAlpha.value })
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 24.dp)) {
                item("word", "heading") {
                    WordHeading(page.entry, speak)
                    QuietText("累计加入 ${page.count} 次", Modifier.padding(top = 4.dp, bottom = 24.dp))
                }
                studyAnswer(page, senses, expanded, { expanded = !expanded }, Modifier.graphicsLayer { alpha = revealAlpha.value })
            }
        }
    }
}

private fun LazyListScope.studyAnswer(page: StudyPage, senses: List<Sense>, expanded: Boolean,
                                     toggle: () -> Unit, revealModifier: Modifier) {
    if (!page.revealed) item("prompt", "prompt") {
        Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            HorizontalDivider(Modifier.width(32.dp))
            Text("回忆它的含义", style = MaterialTheme.typography.headlineSmall)
            QuietText("先在心里说出释义，再显示答案。")
        }
    } else {
        items(if (expanded) senses else senses.take(3), key = { "sense:${it.id}" }, contentType = { "sense" }) { sense ->
            Column(revealModifier.padding(bottom = 12.dp)) {
                Text(sense.text, style = MaterialTheme.typography.bodyLarge)
                if (page.entry.hasExamStatistics) QuietText(sense.examCount?.let { "真题出现 $it 次" } ?: "未统计")
            }
        }
        item("source", "note") {
            if (senses.size > 3) TextButton(toggle) { Text(if (expanded) "收起释义" else "展开其余 ${senses.size - 3} 条释义") }
            QuietText(page.entry.senseLabel)
            page.entry.examStatistics?.let { QuietText(it.corpus.scope) }
        }
        if (page.entry.example.isNotBlank()) item("example", "example") {
            Column(revealModifier.padding(top = 20.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                Text(page.entry.example, fontFamily = BookSerif, fontSize = 21.sp, lineHeight = 29.sp)
                Text(page.entry.exampleTranslation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                QuietText("原创用法例句 · 非真题")
            }
        }
    }
}

/** Reserve the same dock bounds on both sides of a reveal. No page-wide height animation. */
@Composable private fun StudyDock(state: AppState, reveal: () -> Unit, answer: (Rating) -> Unit) {
    val session = state.session ?: return
    val memory = remember(state.snapshot?.words, session.current?.word) {
        state.snapshot?.words?.find { it.word == session.current?.word }?.memory
    }
    val intervals = remember(memory, state.retention, state.now) {
        val scheduler = FsrsScheduler(state.retention)
        Rating.entries.associateWith { rating -> runCatching {
            intervalLabel(state.now, scheduler.review(memory, rating, state.now).dueAt)
        }.getOrNull() }
    }
    val fontScale = LocalDensity.current.fontScale
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column {
            HorizontalDivider()
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                val columns = if (maxWidth >= 560.dp) 4 else 2
                val buttonHeight = 66.dp + (32 * (fontScale - 1).coerceAtLeast(0f)).dp
                val height = buttonHeight * (4 / columns) + if (columns == 2) 10.dp else 0.dp
                Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.BottomCenter) {
                    if (!session.revealed) AccentButton("显示答案", reveal, Modifier.fillMaxWidth(), !session.saving)
                    else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Rating.entries.chunked(columns).forEach { ratings ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                ratings.forEach { rating ->
                                    val scheme = MaterialTheme.colorScheme
                                    val container = when (rating) {
                                        Rating.AGAIN -> scheme.errorContainer
                                        Rating.HARD -> scheme.surfaceContainer
                                        Rating.GOOD -> scheme.secondaryContainer
                                        Rating.EASY -> scheme.secondaryContainer.copy(alpha = 0.65f)
                                    }
                                    FilledTonalButton({ answer(rating) }, Modifier.weight(1f).height(buttonHeight),
                                        enabled = !session.saving && intervals[rating] != null, shape = MaterialTheme.shapes.small,
                                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = container, contentColor = scheme.onSurface),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(rating.label, style = MaterialTheme.typography.labelLarge)
                                            Text(intervals[rating] ?: "检查时间", style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
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
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
            Text(dueLabel(state.now, due), Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text("还有 ${progress.items.size} 个词需要再巩固，已安排到上面的日期。到期当天随时可以继续。",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        QuietText("未记住的学习任务仍然保留，离开页面也不会丢失。")
        if ((state.snapshot?.readyLearningWords ?: 0) > 0) {
            AccentButton("先学其他词", other, Modifier.fillMaxWidth())
            QuietText("另开一组可学词，当前等待任务会留到后续组。")
        }
        OutlinedButton(back, Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("今天先到这里") }
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
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
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
