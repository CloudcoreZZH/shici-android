package io.github.shici.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable fun SettingsScreen(state: AppState, appearance: (Appearance) -> Unit, retention: (Double) -> Unit,
                              groupSize: (Int) -> Unit, reset: () -> Unit) {
    var confirmReset by remember { mutableStateOf(false) }
    var target by remember(state.retention) { mutableFloatStateOf((state.retention * 100).toFloat()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenHeader("我的")
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("拾词", style = MaterialTheme.typography.headlineLarge)
            QuietText("查一个词，认真记住它。")
            HorizontalDivider()
            Text("外观", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Appearance.entries.forEach { value -> FilterChip(value == state.appearance, { appearance(value) }, label = { Text(value.label) }) }
            }
            Text("每组学习量", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 20).forEach { value -> FilterChip(state.groupSize == value, { groupSize(value) }, label = { Text("$value 词") }) }
            }
            QuietText("新组生效。已开始的组会保留进度，同一组不会安排多条同词的加入任务。")
            Text("复习目标保持率 · ${target.roundToInt()}%", style = MaterialTheme.typography.titleMedium)
            Slider(value = target, onValueChange = { target = it }, valueRange = 70f..97f, steps = 26,
                onValueChangeFinished = { retention(target.roundToInt() / 100.0) })
            Text("FSRS-6 根据你的答题情况调整间隔。保持率越高，通常需要更多复习。90% 可作为起点；设置改变从下一次答题开始生效。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            QuietText("按本地日期复习，最短间隔 1 天。到期当天随时可学，不必等到上次答题的时刻。按天取整后的实际记忆保持率不保证恰好等于目标值。")
            Text("流畅显示", style = MaterialTheme.typography.titleMedium)
            Text("支持 120 Hz 高刷新率", style = MaterialTheme.typography.bodyLarge)
            QuietText("动画和滚动使用系统帧同步；静止页面不持续刷新。实际刷新率由设备能力、系统设置和省电策略共同决定。动画跟随系统的移除动画设置。")
            HorizontalDivider()
            Text("数据与隐私", style = MaterialTheme.typography.titleMedium)
            Text("所有词书和学习记录只保存在本机。应用没有广告、账号、统计 SDK 或后台保活，不申请联网和共享存储权限。")
            Text("正常卸载会清除本应用的词典副本、词书、学习记录和设置。已关闭自动云备份与设备迁移备份。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton({ confirmReset = true }) { Text("清除个人学习数据", color = MaterialTheme.colorScheme.error) }
            HorizontalDivider()
            Text("离线词典", style = MaterialTheme.typography.titleMedium)
            Text("可检索 ${state.dictionarySize} 个词条")
            Text("ECDICT · ${state.baseDictionarySize} 个词条")
            Text("维基补充释义 · ${state.referenceSize} 个词条")
            Text("学习编辑版 · ${state.editorialSize} 个词条与原创例句")
            QuietText("部分多义词提供考研释义编辑优先级和原创例句，明确标注非频率统计、非真题。其余词保留完整原词典释义。")
            QuietText("2024 参考词表含 5,530 条记录，规范化后 5,528 词均已收录。2027 英语（一）完整大纲及全量义项优先级尚未核验；参考词表不替代当年官方大纲。")
            DataLicenses()
            QuietText("发音使用手机已安装的离线英语语音。缺少语音时由系统设置管理，本应用不会自动下载。")
            QuietText("版本 ${io.github.shici.app.BuildConfig.VERSION_NAME} · Android 16\nECDICT：MIT · github.com/skywind3000/ECDICT\nFSRS：open-spaced-repetition/fsrs4anki")
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("清除所有个人数据？") },
        text = { Text("所有词书、加入次数、待学任务、复习记录和设置将被删除，无法撤销。离线词典会保留。") },
        confirmButton = { TextButton({ reset(); confirmReset = false }) { Text("确认清除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ confirmReset = false }) { Text("取消") } })
}
