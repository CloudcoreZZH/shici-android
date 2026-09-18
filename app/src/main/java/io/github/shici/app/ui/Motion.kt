package io.github.shici.app.ui

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** Short, consistent transitions. Compose applies the platform animator duration scale. */
internal object Motion {
    val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> enter() = tween<T>(240, easing = easing)
    fun <T> exit() = tween<T>(140, easing = easing)
}

/** A redraw vote, not a rendering loop or a forced display mode. Static screens stay idle. */
@Composable internal fun HighRefreshRateEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        if (Build.VERSION.SDK_INT >= 36) {
            val previous = view.requestedFrameRate
            view.requestedFrameRate = 120f
            onDispose { view.requestedFrameRate = previous }
        } else onDispose { }
    }
}
