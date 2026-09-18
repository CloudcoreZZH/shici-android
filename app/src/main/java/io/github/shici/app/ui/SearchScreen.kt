package io.github.shici.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.WordEntry

@Composable fun SearchScreen(state: AppState, search: (String) -> Unit, open: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("查词")
        BookTitle("遇见一个词", "查清含义，再把它记住。", Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
        OutlinedTextField(state.query, search, Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
            placeholder = { Text("输入英文单词") }, singleLine = true, shape = MaterialTheme.shapes.small,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { state.searchResults.firstOrNull()?.let { focus.clearFocus(); open(it.word) } }),
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, trailingIcon = {
                if (state.query.isNotEmpty()) IconButton({ search("") }) { Icon(Icons.Outlined.Close, "清空搜索") }
            })
        Box(Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 24.dp)) {
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
            if (state.query.isBlank()) item("empty", "empty") {
                EmptyState("从一个词开始", "离线收录 ${state.dictionarySize} 个词条。\n每次加入词书，都再学一次。")
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("address", "persist").forEach { word -> TextButton({ open(word) }) {
                        Text(word, fontFamily = BookSerif, fontSize = 22.sp)
                    } }
                }
            }
            else if (!state.searching && state.searchResults.isEmpty()) item("empty", "empty") {
                EmptyState("暂未找到", "试试原形或检查拼写。当前词典支持英文词条检索。")
            }
            items(state.searchResults, key = { it.word }, contentType = { "word" }) { entry ->
                Column(Modifier.fillMaxWidth().clickable { focus.clearFocus(); open(entry.word) }.padding(vertical = 18.dp)) {
                    Text(entry.word, fontFamily = BookSerif, fontSize = 26.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(entry.learningSenses().firstOrNull()?.text.orEmpty(), maxLines = 2, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable fun DictionaryScreen(entry: WordEntry, count: Int, bookName: String, speak: (String) -> Unit,
                                back: () -> Unit, add: () -> Unit, adding: Boolean = false, learn: () -> Unit = {}) {
    var section by rememberSaveable(entry.word) { mutableIntStateOf(0) }
    var referencesExpanded by rememberSaveable(entry.word) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val senses = remember(entry, section) { if (section == 0) entry.learningSenses() else entry.senses }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("查词详情", back)
        // Each sense is its own lazy item: long definitions do not build a second offscreen page.
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)) {
            item("heading", "heading") {
                WordHeading(entry, speak)
                Spacer(Modifier.height(16.dp))
                PaperTabs(listOf("学习释义", "全部释义", "词形与英文"), section) { section = it }
                Spacer(Modifier.height(24.dp))
            }
            if (section == 0) item("statistics", "note") {
                Text(entry.senseLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                QuietText(if (entry.hasExamStatistics) "${entry.examStatistics!!.corpus.scope}\n仅表示所列语料中的义项次数，未统计义项置后。"
                    else "暂无可核验的逐义项真题统计，以下保留词典原顺序。", Modifier.padding(top = 6.dp, bottom = 16.dp))
                entry.examStatistics?.let { statistics ->
                    TextButton({ uri.openUri(statistics.corpus.methodologyUrl) }) { Text("统计范围与标注方法 ↗") }
                }
            }
            if (section < 2) {
                items(senses, key = { "sense:$section:${it.id}" }, contentType = { "sense" }) { sense ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(sense.text, style = MaterialTheme.typography.bodyLarge)
                        if (section == 0 && entry.hasExamStatistics) {
                            QuietText(sense.examCount?.let { "真题出现 $it 次" } ?: "未统计")
                            val occurrences = entry.examStatistics!!.senses.firstOrNull { it.id == sense.id }?.occurrences.orEmpty()
                            if (occurrences.isNotEmpty()) EvidenceLinks(entry, sense.id)
                        }
                    }
                }
                if (section == 0 && entry.example.isNotBlank()) item("example", "example") {
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider(Modifier.width(40.dp))
                    Spacer(Modifier.height(20.dp))
                    Text(entry.example, fontFamily = BookSerif, fontSize = 21.sp, lineHeight = 30.sp)
                    Text(entry.exampleTranslation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    QuietText("原创用法例句 · 非真题", Modifier.padding(top = 8.dp))
                }
            } else item("forms", "forms") {
                Text("词形变化", style = MaterialTheme.typography.titleMedium)
                Text(formatExchange(entry.exchange), Modifier.padding(vertical = 12.dp))
                Spacer(Modifier.height(16.dp))
                Text("英文释义", style = MaterialTheme.typography.titleMedium)
                Text(entry.english.ifBlank { "词典暂未提供英文释义。" }, Modifier.padding(vertical = 12.dp))
            }
            if (section == 1 && entry.references.isNotEmpty()) {
                item("reference-title", "note") {
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    if (entry.source == "ECDICT") TextButton({ referencesExpanded = !referencesExpanded }) {
                        Text(if (referencesExpanded) "收起补充释义" else "展开维基补充释义 · ${entry.references.sumOf { it.senses.size }} 条")
                    }
                    QuietText("中文维基词典 · 社区编写，未逐条专业审校。保留原文简繁体与用法标签，顺序不表示考研频率。")
                }
                if (referencesExpanded || entry.source != "ECDICT") entry.references.forEachIndexed { referenceIndex, reference ->
                    item("ref-header:$referenceIndex", "note") {
                        Text("${reference.headword} · 原词条", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleSmall)
                    }
                    if (entry.source == "ECDICT") items(reference.senses.size, key = { "ref:$referenceIndex:$it" }, contentType = { "sense" }) { index ->
                        Text(reference.senses[index], Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    item("ref-link:$referenceIndex", "note") {
                        TextButton({ uri.openUri(reference.url) }) { Text("查看 ${reference.headword} 的原词条与作者记录 ↗") }
                    }
                }
                item("reference-license", "note") {
                    QuietText("中文维基词典贡献者 · Kaikki.org / Wiktextract\nCC BY-SA 4.0 · 已过滤、去重及标注词性，未改写释义。", Modifier.padding(vertical = 12.dp))
                }
            }
            item("source", "note") {
                Spacer(Modifier.height(24.dp))
                if (entry.inNetem2024) QuietText("NETEM 2024 参考词表收录 · 非 2027 大纲", Modifier.padding(bottom = 8.dp))
                QuietText("完整释义：${entry.source} 离线词典")
                if (section == 0 && entry.hasExamStatistics) QuietText("未纳入统计的词典释义请查看「全部释义」。")
                Spacer(Modifier.height(16.dp))
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            QuietText("$bookName · 已加入 $count 次", Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AccentButton(if (adding) "正在加入…" else if (count == 0) "加入词书 +1" else "再次加入词书 +1", add, Modifier.weight(1f), !adding)
                if (count > 0) TextButton(learn, Modifier.heightIn(min = 56.dp)) { Text("去学习") }
            }
        }
    }
}

@Composable private fun EvidenceLinks(entry: WordEntry, senseId: String) {
    var expanded by rememberSaveable(entry.word, senseId) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    val statistics = entry.examStatistics ?: return
    TextButton({ expanded = true }) { Text("核对逐次出处") }
    if (expanded) AlertDialog(onDismissRequest = { expanded = false }, title = { Text("义项统计出处") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(statistics.senses.single { it.id == senseId }.occurrences.orEmpty()) { evidence ->
                    val paper = statistics.corpus.papers.single { it.id == evidence.paperId }
                    TextButton({ uri.openUri(paper.sourceUrl) }) {
                        Text("${paper.year} · ${evidence.location}\n复核：${evidence.reviewedBy} ↗")
                    }
                }
                item { QuietText("${statistics.corpus.title}\n数据许可：${statistics.corpus.license}") }
            }
        }, confirmButton = { TextButton({ expanded = false }) { Text("关闭") } })
}

private fun formatExchange(raw: String): String {
    val names = mapOf("p" to "过去式", "d" to "过去分词", "i" to "现在分词", "3" to "第三人称单数",
        "r" to "比较级", "t" to "最高级", "s" to "复数", "0" to "原形")
    return raw.split('/').mapNotNull { piece ->
        val parts = piece.split(':', limit = 2)
        if (parts.size == 2 && names.containsKey(parts[0])) "${names[parts[0]]}  ${parts[1]}" else null
    }.joinToString("\n").ifBlank { "词典暂未提供词形变化。" }
}
