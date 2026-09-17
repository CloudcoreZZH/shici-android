package io.github.shici.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.shici.core.BookWord
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun BookScreen(state: AppState, choose: (Long) -> Unit, create: (String) -> Unit, open: (String) -> Unit,
                          remove: (String) -> Unit, learn: () -> Unit) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    val words = state.snapshot?.words.orEmpty().filter { word ->
        word.word.contains(query.trim(), ignoreCase = true) && when (filter) {
            1 -> word.pendingCount > 0
            2 -> word.isDue(state.now)
            else -> true
        }
    }
    Column(Modifier.fillMaxSize()) {
        BookPicker(state.books, state.snapshot?.book, choose, create)
        Text("我的词书", Modifier.padding(horizontal = 24.dp), style = MaterialTheme.typography.headlineLarge)
        QuietText("${state.snapshot?.words?.size ?: 0} 词 · 累计加入 ${state.snapshot?.totalAdditions ?: 0} 次",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        OutlinedTextField(query, { query = it.take(100) }, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            singleLine = true, placeholder = { Text("搜索词书") })
        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("全部", "待学习", "待复习").forEachIndexed { index, title -> FilterChip(filter == index, { filter = index }, label = { Text(title) }) }
        }
        QuietText("按加入次数 ↓", Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (words.isEmpty()) item { EmptyState("这里还没有单词", "查词后点击加入，便能在这里学习。") }
            items(words, key = { it.word }) { word ->
                Card(onClick = { open(word.word) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(word.word, style = MaterialTheme.typography.titleLarge)
                            Text(state.meanings[word.word].orEmpty(), maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium)
                            if (word.pendingCount > 0) QuietText("待学习 ${word.pendingCount} 次")
                            else word.memory?.let { QuietText("下次复习 ${it.dueAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))}") }
                        }
                        Text("${word.additionCount} 次", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp))
                        IconButton({ deleting = word.word }) { Icon(Icons.Outlined.DeleteOutline, "从词书移除 ${word.word}", Modifier.size(20.dp)) }
                    }
                }
            }
        }
        AccentButton("开始学习", learn, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            enabled = (state.snapshot?.pendingCount ?: 0) > 0)
    }
    deleting?.let { word -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("移除 $word？") },
        text = { Text("将移除当前词书中这个词的所有加入记录、待学任务和复习记录。其他词书不受影响。") },
        confirmButton = { TextButton({ remove(word); deleting = null }) { Text("移除") } },
        dismissButton = { TextButton({ deleting = null }) { Text("保留") } }) }
}
