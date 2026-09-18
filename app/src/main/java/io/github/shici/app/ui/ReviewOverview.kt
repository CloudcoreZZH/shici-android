package io.github.shici.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.SessionMode

@Composable fun ReviewOverview(state: AppState, back: () -> Unit, start: () -> Unit) {
    val saved = state.savedSessions[SessionMode.REVIEW]
    val due = state.dueWords
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("复习安排", back)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
            item("title", "header") {
                BookTitle("今天到期  ${due.size}", "加入次数多的优先 · 每组最多 ${state.groupSize} 词")
                Spacer(Modifier.height(24.dp))
                HorizontalDivider()
            }
            if (saved != null) item("resume", "note") {
                QuietText("有一组尚未完成 · 已完成 ${saved.completed} / ${saved.total}", Modifier.padding(vertical = 12.dp))
            }
            if (due.isEmpty()) item("empty", "note") {
                EmptyState("暂时没有到期词", state.nextReviewAt?.let { "下一次复习：${dueLabel(state.now, it)}。" }
                    ?: "新加入的词先完成学习，再按记忆间隔进入复习。")
            }
            itemsIndexed(due, key = { _, word -> word.word }, contentType = { _, _ -> "word" }) { index, word ->
                Row(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    QuietText("${index + 1}")
                    Text(word.word, Modifier.weight(1f), fontFamily = BookSerif, fontSize = 24.sp)
                    QuietText("加入 ${word.additionCount} 次")
                }
                HorizontalDivider()
            }
            item("calendar", "calendar") {
                Spacer(Modifier.height(28.dp))
                ReviewCalendar(state)
                Spacer(Modifier.height(16.dp))
                QuietText("先回忆，再揭晓释义。")
            }
        }
        AccentButton(if (saved != null) "继续复习" else "开始复习", start,
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), enabled = saved != null || due.isNotEmpty())
    }
}
