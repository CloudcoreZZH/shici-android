package io.github.shici.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.shici.app.performance.LocalFrameRecorder

@Composable internal fun PerformancePanel() {
    val recorder = LocalFrameRecorder.current ?: return
    val state by recorder.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Text("流畅度记录", style = MaterialTheme.typography.titleMedium)
    QuietText("点击开始后，返回卡顿页面重复滚动、翻答案或切换页面。60 秒后自动停止；切到后台也会停止。只保留本次内存记录，不上传。")
    Button(if (state.recording) recorder::stop else recorder::start, enabled = !state.finishing) {
        Text(if (state.recording) "停止并查看记录" else if (state.finishing) "正在汇总…" else "开始记录 60 秒")
    }
    if (state.recording) QuietText("正在记录，可前往其他页面操作。")
    if (state.report.isNotBlank()) {
        Text(state.report, style = MaterialTheme.typography.bodySmall)
        TextButton({ (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
            .setPrimaryClip(ClipData.newPlainText("拾词流畅度记录", state.report)) }) { Text("复制记录") }
    }
}
