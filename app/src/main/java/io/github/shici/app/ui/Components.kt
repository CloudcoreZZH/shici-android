package io.github.shici.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.WordEntry

@Composable fun ScreenHeader(title: String, back: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
        else Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(onClick, enabled = enabled, modifier = modifier.heightIn(min = 56.dp), shape = MaterialTheme.shapes.small,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable fun WordHeading(entry: WordEntry, speak: (String) -> Unit, modifier: Modifier = Modifier, centered: Boolean = false) {
    Column(modifier.fillMaxWidth(), horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        val size = when { entry.word.length > 18 -> 30.sp; entry.word.length > 12 -> 40.sp; else -> 52.sp }
        Text(entry.word, fontFamily = BookSerif, fontWeight = FontWeight.Normal, fontSize = size, lineHeight = size * 1.12f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (entry.phonetic.isNotBlank()) Text("/${entry.phonetic}/", color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f, fill = false), fontSize = 18.sp)
            IconButton(onClick = { speak(entry.word) }) {
                Icon(Icons.AutoMirrored.Outlined.VolumeUp, "播放 ${entry.word} 的离线发音", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable internal fun BookTitle(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(2.dp).height(56.dp).background(MaterialTheme.colorScheme.primary))
        Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            QuietText(subtitle)
        }
    }
}

@Composable fun QuietText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable fun EmptyState(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        if (action != null) AccentButton(action, onAction)
    }
}

@Composable internal fun PaperTabs(labels: List<String>, selected: Int, select: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton({ select(index) }, Modifier.semantics { this.selected = selected == index; role = Role.Tab }) {
                    Text(label, color = if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.width(52.dp), thickness = if (selected == index) 2.dp else 1.dp,
                    color = if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
