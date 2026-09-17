package io.github.shici.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable fun SettingsScreen(state: AppState, appearance: (Appearance) -> Unit, retention: (Double) -> Unit, reset: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    var target by remember(state.retention) { mutableFloatStateOf((state.retention * 100).toFloat()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("我的")
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("拾词", style = MaterialTheme.typography.headlineLarge)
            QuietText("查一个词，认真记住它。")
            HorizontalDivider()
            Text("外观", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Appearance.entries.forEach { value -> FilterChip(value == state.appearance, { appearance(value) }, label = { Text(value.label) }) }
            }
            Text("复习目标保持率 · ${target.roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            Slider(value = target, onValueChange = { target = it }, valueRange = 70f..97f, steps = 26,
                onValueChangeFinished = { retention(target.roundToInt() / 100.0) })
            Text("FSRS-6 根据你的答题情况调整间隔。保持率越高，通常需要更多复习。90% 可作为起点；设置改变从下一次答题开始生效。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("数据与隐私", style = MaterialTheme.typography.titleMedium)
            Text("所有词书和学习记录只保存在本机。应用没有广告、账号、统计 SDK 或后台保活，不申请联网和共享存储权限。")
            Text("正常卸载会清除本应用的词典副本、词书、学习记录和设置。已关闭自动云备份与设备迁移备份。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton({ confirmReset = true }) { Text("清除个人学习数据", color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            Text("离线词典", style = MaterialTheme.typography.titleMedium)
            Text("ECDICT · ${state.dictionarySize} 个词条")
            QuietText("目前没有经过核验的考研真题义项频率，释义保持词典原顺序。词频与考试标签不等于义项频率。")
            QuietText("发音使用手机已安装的离线英语语音。缺少语音时由系统设置管理，本应用不会自动下载。")
            QuietText("版本 0.1.0 · Android 16\nECDICT：MIT · github.com/skywind3000/ECDICT\nFSRS：open-spaced-repetition/fsrs4anki")
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("清除所有个人数据？") },
        text = { Text("所有词书、加入次数、待学任务、复习记录和设置将被删除，无法撤销。离线词典会保留。") },
        confirmButton = { TextButton({ reset(); confirmReset = false }) { Text("确认清除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("取消") } })
}
