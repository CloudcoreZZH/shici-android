package io.github.shici.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.*

@Composable fun ReviewOverview(state: AppState, back: () -> Unit, start: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("今日复习", back)
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.dueWords.size.toString(), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text("词待复习", Modifier.padding(bottom = 12.dp), style = MaterialTheme.typography.titleLarge)
            }
            QuietText("先复习你反复加入的词")
            Spacer(Modifier.height(24.dp))
            Text("加入次数优先 ↓", style = MaterialTheme.typography.titleMedium)
            QuietText("到期词中，加入越多越靠前")
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.dueWords.isEmpty()) item { EmptyState("这次复习完成了", "还没到期的词会按记忆情况安排。新加入的词请从 Learn 开始。") }
            itemsIndexed(state.dueWords, key = { _, item -> item.word }) { index, word ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuietText("%02d".format(index + 1))
                        Column(Modifier.weight(1f)) {
                            Text(word.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(state.meanings[word.word].orEmpty(), maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("加入 ${word.additionCount} 次", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        AccentButton("开始复习", start, Modifier.fillMaxWidth().padding(24.dp), enabled = state.dueWords.isNotEmpty())
    }
}

@Composable fun StudyScreen(state: AppState, speak: (String) -> Unit, back: () -> Unit,
                           reveal: (Rating) -> Unit, answer: (Rating) -> Unit) {
    val session = state.session ?: return
    if (session.finished) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            EmptyState("这一组完成了", "已完成 ${session.items.size} 次${if (session.mode == SessionMode.LEARN) "学习" else "复习"}。\n学习记录已保存，下次复习会按记忆情况安排。", "回到首页", back)
        }
        return
    }
    val entry = session.entry ?: return
    val scroll = rememberScrollState()
    LaunchedEffect(session.index) { scroll.scrollTo(0) }
    val isLearning = session.mode == SessionMode.LEARN
    val count = state.snapshot?.words?.find { it.word == entry.word }?.additionCount ?: 1
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (isLearning) "学习" else "复习", back) {
            QuietText("${session.index + 1} / ${session.items.size}", Modifier.padding(end = 20.dp))
        }
        LinearProgressIndicator(progress = { session.index.toFloat() / session.items.size },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), color = Coral)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                Text("累计加入 $count 次${if (isLearning && count > 1) " · 重学" else ""}", Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(24.dp))
            WordHeading(entry, speak, centered = true)
            Spacer(Modifier.height(24.dp))
            if (!session.revealed) {
                Spacer(Modifier.height(56.dp))
                Text("先回想，再揭晓", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
            }
            AnimatedVisibility(session.revealed, enter = fadeIn() + expandVertically()) {
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (entry.hasExamStatistics) "考研释义" else "词典释义", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge)
                        entry.examSenses().forEach { sense -> Text(sense.text, style = MaterialTheme.typography.bodyLarge) }
                        if (!entry.hasExamStatistics) QuietText("暂无考研义项统计 · 按词典原顺序")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (isLearning) {
                if (!session.revealed) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton({ reveal(Rating.AGAIN) }, Modifier.weight(1f).heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp)) { Text("不认识") }
                        AccentButton("认识", { reveal(Rating.GOOD) }, Modifier.weight(1f))
                    }
                    QuietText("先回想，再看释义", Modifier.align(Alignment.CenterHorizontally))
                } else {
                    QuietText(if (session.tentativeRating == Rating.AGAIN) "会更快安排复习，再巩固一次。" else "回想正确后继续；记错了可以纠正。")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton({ reveal(Rating.AGAIN) }, Modifier.weight(1f).heightIn(min = 54.dp), enabled = !session.saving,
                            shape = RoundedCornerShape(16.dp)) { Text(if (session.tentativeRating == Rating.AGAIN) "已标记忘记" else "记错了") }
                        AccentButton(if (session.saving) "保存中…" else "下一词", { answer(session.tentativeRating) }, Modifier.weight(1f), !session.saving)
                    }
                }
            } else if (!session.revealed) {
                AccentButton("显示答案", { reveal(Rating.GOOD) }, Modifier.fillMaxWidth())
                QuietText("尽量说出常见释义", Modifier.align(Alignment.CenterHorizontally))
            } else {
                Text("刚才回想得怎么样？", Modifier.align(Alignment.CenterHorizontally))
                QuietText("忘记＝没想起；困难＝想起但费力", Modifier.align(Alignment.CenterHorizontally))
                Rating.entries.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { rating ->
                            if (rating == Rating.GOOD) AccentButton(rating.label, { answer(rating) }, Modifier.weight(1f), !session.saving)
                            else OutlinedButton({ answer(rating) }, Modifier.weight(1f).heightIn(min = 54.dp), enabled = !session.saving,
                                shape = RoundedCornerShape(16.dp)) { Text(rating.label) }
                        }
                    }
                }
            }
        }
    }
}
