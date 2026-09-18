package io.github.shici.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.shici.core.WordEntry

@Composable fun SearchScreen(state: AppState, search: (String) -> Unit, open: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("查词")
        OutlinedTextField(state.query, search, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            placeholder = { Text("输入英文单词") }, singleLine = true, shape = RoundedCornerShape(22.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { state.searchResults.firstOrNull()?.let { focus.clearFocus(); open(it.word) } }),
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = {
                if (state.query.isNotEmpty()) IconButton({ search("") }) { Icon(Icons.Outlined.Close, "清空搜索") }
            })
        if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 24.dp))
        if (state.query.isBlank()) {
            EmptyState("从一个词开始", "离线词典已收录 ${state.dictionarySize} 个词条。\n查词后可以反复加入词书，每次都会再学一次。")
            Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("address", "persist").forEach { word -> SuggestionChip(onClick = { open(word) }, label = { Text(word) }) }
            }
        } else if (!state.searching && state.searchResults.isEmpty()) {
            EmptyState("暂未找到", "试试原形或检查拼写。当前词典只支持英文词条检索。")
        }
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.searchResults, key = { it.word }) { entry ->
                Card(onClick = { focus.clearFocus(); open(entry.word) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(entry.word, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text(entry.learningSenses().firstOrNull()?.text.orEmpty(), maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable fun DictionaryScreen(entry: WordEntry, count: Int, bookName: String, speak: (String) -> Unit,
                                back: () -> Unit, add: () -> Unit, adding: Boolean = false, learn: () -> Unit = {}) {
    var section by rememberSaveable(entry.word) { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("查词详情", back)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(24.dp))
            WordHeading(entry, speak)
            if ("ky" in entry.tags) AssistChip(onClick = {}, label = { Text("考研词汇") })
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("学习释义", "全部释义", "词形与英文").forEachIndexed { index, label ->
                    FilterChip(section == index, { section = index }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(12.dp))
            if (section == 0) {
                QuietText(when {
                    entry.hasExamStatistics -> "按已核验的考研义项频次排序"
                    entry.editorialSenses.isNotEmpty() -> "考研释义优先级 · 编辑整理，非频率统计"
                    else -> "本词暂无编辑优先级，以下按词典原顺序展示。"
                })
                Spacer(Modifier.height(12.dp))
            }
            if (section < 2) {
                val senses = if (section == 0) entry.learningSenses() else entry.senses
                senses.forEachIndexed { index, sense ->
                    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            QuietText("%02d".format(index + 1))
                            Column(Modifier.weight(1f)) {
                                Text(sense.text, style = MaterialTheme.typography.bodyLarge)
                                if (sense.examCount != null && !sense.examSource.isNullOrBlank()) {
                                    QuietText("真题出现 ${sense.examCount} 次 · ${sense.examSource}")
                                }
                            }
                        }
                    }
                }
                if (section == 0 && entry.example.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(entry.example, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(entry.exampleTranslation, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietText("原创用法例句 · 非真题", Modifier.padding(top = 8.dp))
                }
            } else {
                Text("词形变化", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(formatExchange(entry.exchange))
                Spacer(Modifier.height(24.dp))
                Text("英文释义", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(entry.english.ifBlank { "词典暂未提供英文释义。" })
            }
            Spacer(Modifier.height(12.dp))
            QuietText("完整释义：${entry.source} 离线词典${if (entry.editorialSenses.isNotEmpty()) " · 学习释义：拾词编辑版" else ""}")
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            QuietText("$bookName · 已加入 $count 次", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentButton(if (adding) "正在加入…" else if (count == 0) "加入词书 +1" else "再次加入词书 +1", add, Modifier.weight(1f), !adding)
                if (count > 0) OutlinedButton(learn, Modifier.heightIn(min = 54.dp)) { Text("去学习") }
            }
        }
    }
}

private fun formatExchange(raw: String): String {
    val names = mapOf("p" to "过去式", "d" to "过去分词", "i" to "现在分词", "3" to "第三人称单数",
        "r" to "比较级", "t" to "最高级", "s" to "复数", "0" to "原形")
    return raw.split('/').mapNotNull { piece ->
        val parts = piece.split(':', limit = 2)
        if (parts.size == 2 && names.containsKey(parts[0])) "${names[parts[0]]}  ${parts[1]}" else null
    }.joinToString("\n").ifBlank { "词典暂未提供词形变化。" }
}
