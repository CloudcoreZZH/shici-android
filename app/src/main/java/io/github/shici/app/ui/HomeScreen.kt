package io.github.shici.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.SessionMode
import io.github.shici.core.WordBook
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable fun BookPicker(books: List<WordBook>, selected: WordBook?, choose: (Long) -> Unit, create: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Box {
        TextButton({ expanded = true }) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, null, Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(selected?.name ?: "考研生词本", maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, "选择词书")
        }
        DropdownMenu(expanded, { expanded = false }) {
            books.forEach { book -> DropdownMenuItem(text = { Text(book.name) }, onClick = { choose(book.id); expanded = false }) }
            HorizontalDivider()
            DropdownMenuItem(text = { Text("新建词书") }, onClick = { creating = true; expanded = false })
        }
    }
    if (creating) AlertDialog(onDismissRequest = { creating = false }, title = { Text("新建词书") }, text = {
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("词书名称") }, singleLine = true)
    }, confirmButton = { TextButton({ create(name); name = ""; creating = false }, enabled = name.isNotBlank()) { Text("创建") } },
        dismissButton = { TextButton({ creating = false }) { Text("取消") } })
}

@Composable fun HomeScreen(state: AppState, chooseBook: (Long) -> Unit, createBook: (String) -> Unit,
                          learn: () -> Unit, review: () -> Unit, open: (String) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val minHeight = maxHeight
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = minHeight).padding(horizontal = 24.dp)) {
            BookPicker(state.books, state.snapshot?.book, chooseBook, createBook)
            Spacer(Modifier.height(36.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.now.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM / dd   EEEE", Locale.CHINA)),
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(28.dp))
                QuietText("今日一词")
                Text("persist", fontSize = 58.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif,
                    modifier = Modifier.clickable(onClickLabel = "查询 persist") { open("persist") }.padding(vertical = 8.dp))
                Text("坚持，继续", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.height(100.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StudyTile("Learn", "学习 ${state.snapshot?.pendingCount ?: 0} 次", "每次加入，再学一次", learn, Modifier.weight(1f))
                StudyTile("Review", "复习 ${state.dueWords.size} 词", "多次加入优先", review, Modifier.weight(1f))
            }
            Text("今日已完成 ${state.snapshot?.completedToday ?: 0} 次学习", Modifier.align(Alignment.CenterHorizontally).padding(vertical = 24.dp),
                style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun StudyTile(title: String, metric: String, caption: String, click: () -> Unit, modifier: Modifier) {
    Card(click, modifier = modifier, shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(metric, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            QuietText(caption)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.align(Alignment.End).padding(top = 12.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
