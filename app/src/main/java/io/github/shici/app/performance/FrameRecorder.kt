package io.github.shici.app.performance

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.shici.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class CaptureState(val recording: Boolean = false, val finishing: Boolean = false, val report: String = "")
val LocalFrameRecorder = staticCompositionLocalOf<FrameRecorder?> { null }

/** Opt-in 60-second foreground capture. No overlay, per-frame Compose state updates, files or uploads. */
class FrameRecorder(private val window: Window) {
    private val main = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(CaptureState())
    val state = mutable.asStateFlow()
    private var capture: Capture? = null
    private var closed = false
    private val timeout = Runnable { stop() }

    private data class Capture(val thread: HandlerThread, val handler: Handler,
                               val listener: Window.OnFrameMetricsAvailableListener, val samples: FrameSamples,
                               val started: Long, val displayRate: Float)

    fun start() {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (closed || capture != null || mutable.value.finishing) return
        val thread = HandlerThread("ShiciFrameCapture").apply { start() }
        val handler = Handler(thread.looper)
        val samples = FrameSamples()
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, lost ->
            samples.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION), metrics.getMetric(FrameMetrics.DEADLINE),
                metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1L, lost)
        }
        capture = Capture(thread, handler, listener, samples, SystemClock.elapsedRealtime(), window.decorView.display?.refreshRate ?: 0f)
        window.addOnFrameMetricsAvailableListener(listener, handler)
        mutable.value = CaptureState(recording = true)
        main.postDelayed(timeout, 60_000)
    }

    fun stop() {
        check(Looper.myLooper() == Looper.getMainLooper())
        val active = capture ?: return
        capture = null
        main.removeCallbacks(timeout)
        window.removeOnFrameMetricsAvailableListener(active.listener)
        mutable.value = mutable.value.copy(recording = false, finishing = true)
        val seconds = (SystemClock.elapsedRealtime() - active.started) / 1000.0
        active.handler.post {
            val summary = active.samples.summary()
            val report = buildString {
                appendLine("拾词 ${BuildConfig.VERSION_NAME} · 本机帧耗时记录")
                appendLine("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE}")
                appendLine("记录 ${format(seconds)} 秒 · 开始时屏幕 ${format(active.displayRate.toDouble())} Hz")
                appendLine("有效绘制帧 ${summary.frames} · 首绘排除 ${summary.excludedFirstDraws}")
                if (summary.frames > 0) {
                    appendLine("P95 ${format(summary.p95Millis)} ms · 最慢 ${format(summary.maxMillis)} ms")
                    appendLine(if (summary.withDeadline == 0) "系统未提供帧截止时间" else
                        "超过系统帧预算 ${summary.missedDeadline}/${summary.withDeadline}（${format(100.0 * summary.missedDeadline / summary.withDeadline)}%）")
                } else appendLine("没有绘制样本，请在记录期间滚动或切换页面。")
                appendLine("统计回调遗漏 ${summary.lostCallbacks}（不等于掉帧数）")
                if (summary.capacityReached) appendLine("已达到 20000 帧上限，后续样本未计入。")
                append("仅记录应用提交帧耗时，不是实测屏幕 FPS；静止期间没有绘制属正常。")
            }
            main.post { if (!closed) mutable.value = CaptureState(report = report) }
            active.thread.quitSafely()
        }
    }

    fun close() { stop(); closed = true }
    private fun format(value: Double) = String.format(Locale.CHINA, "%.1f", value)
}
