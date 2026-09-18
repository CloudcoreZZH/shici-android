package io.github.shici.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.SessionMode

@Composable fun ReviewOverview(state: AppState, back: () -> Unit, start: () -> Unit) {
    val saved = state.savedSessions[SessionMode.REVIEW]
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("复习安排", back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${state.dueWords.size}", fontSize = 52.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("个单词，现在到期", style = MaterialTheme.typography.titleMedium)
                        Text("只复习到期词，反复加入的排在前面。每组最多 ${state.groupSize} 个。", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (saved != null) item { QuietText("有一组尚未完成 · 已完成 ${saved.completed} / ${saved.total}") }
            if (state.dueWords.isEmpty()) item {
                EmptyState("暂时没有到期词", state.nextReviewAt?.let { "下一次复习：${dueLabel(state.now, it)}。" }
                    ?: "新加入的词先完成学习，再按记忆间隔进入复习。")
            } else item { Text("复习顺序", style = MaterialTheme.typography.titleMedium) }
            itemsIndexed(state.dueWords, key = { _, word -> word.word }) { index, word ->
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        QuietText("%02d".format(index + 1))
                        Column(Modifier.weight(1f)) {
                            Text(word.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            QuietText("${if (word.memory?.phase == io.github.shici.core.Phase.REVIEW) "间隔复习" else "短时巩固"} · 释义在回忆后揭晓")
                        }
                        Text("加入 ${word.additionCount} 次", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        AccentButton(if (saved != null) "继续复习" else "开始复习", start, Modifier.fillMaxWidth().padding(24.dp),
            enabled = saved != null || state.dueWords.isNotEmpty())
    }
}
