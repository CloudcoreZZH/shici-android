package io.github.shici.app.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween

/** Short, consistent transitions. Compose applies the platform animator duration scale. */
internal object Motion {
    val easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    fun <T> enter() = tween<T>(180, easing = easing)
    fun <T> pageEnter() = tween<T>(180, delayMillis = 70, easing = easing)
    fun <T> exit() = tween<T>(70, easing = easing)
}

// High refresh votes live on the drawing Compose root via Modifier.preferredFrameRate.
// No timer, forced display mode, or frame callback is used to keep an idle screen awake.
