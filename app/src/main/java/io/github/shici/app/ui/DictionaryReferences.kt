package io.github.shici.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import io.github.shici.core.WordEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun DictionaryReferences(entry: WordEntry) {
    var expanded by rememberSaveable(entry.word) { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        if (entry.source == "ECDICT") {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收起补充释义" else "展开维基补充释义 · ${entry.references.sumOf { it.senses.size }} 条")
            }
        }
        QuietText("中文维基词典 · 社区编写，未逐条专业审校。保留原文简繁体与用法标签，顺序不表示考研频率。")
        AnimatedVisibility(expanded || entry.source != "ECDICT") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                entry.references.forEach { reference ->
                    Text("${reference.headword} · 原词条", style = MaterialTheme.typography.titleSmall)
                    if (entry.source == "ECDICT") reference.senses.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    TextButton(onClick = { uri.openUri(reference.url) }) { Text("查看 ${reference.headword} 的原词条与作者记录 ↗") }
                }
                QuietText("中文维基词典贡献者 · Kaikki.org / Wiktextract 抽取\nCC BY-SA 4.0 · 已过滤、去重及标注词性，未改写释义。")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** License text ships in the APK and remains readable without a browser or connectivity. */
@Composable internal fun DataLicenses() {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(selected) {
        text = ""
        selected?.let { file -> text = withContext(Dispatchers.IO) {
            context.assets.open(file).bufferedReader().use { it.readText() }
        } }
    }
    QuietText("中文维基词典贡献者 · Kaikki.org / Wiktextract · CC BY-SA 4.0。\nNETEMVocabulary 作者 · CC BY-NC-SA 4.0，仅用于 2024 年词表对照；含非商业使用条件。")
    TextButton({ uri.openUri("https://kaikki.org/zhwiktionary/") }) { Text("维基数据来源与说明 ↗") }
    TextButton({ uri.openUri("https://github.com/exam-data/NETEMVocabulary") }) { Text("2024 参考词表来源 ↗") }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton({ selected = "WIKTIONARY-LICENSE.txt" }) { Text("CC BY-SA 4.0 全文") }
        TextButton({ selected = "NETEM-LICENSE.txt" }) { Text("CC BY-NC-SA 4.0 全文") }
    }
    if (selected != null) AlertDialog(onDismissRequest = { selected = null },
        title = { Text(if (selected == "NETEM-LICENSE.txt") "CC BY-NC-SA 4.0" else "CC BY-SA 4.0") },
        text = { SelectionContainer { Text(text.ifBlank { "正在读取…" },
            Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) } },
        confirmButton = { TextButton({ selected = null }) { Text("关闭") } })
}
