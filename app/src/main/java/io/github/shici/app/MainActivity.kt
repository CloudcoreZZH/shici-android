package io.github.shici.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.shici.app.audio.Pronunciation
import io.github.shici.app.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var pronunciation: Pronunciation
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pronunciation = Pronunciation(this) { message -> Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        setContent {
            val model: AppViewModel = viewModel()
            val state by model.state.collectAsStateWithLifecycle()
            val dark = isDark(state.appearance)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
                onDispose {}
            }
            LaunchedEffect(model) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    while (true) { model.refresh(); delay(30_000) }
                }
            }
            ShiciTheme(state.appearance) { ShiciApp(state, model, pronunciation::speak) }
        }
    }
    override fun onDestroy() { pronunciation.close(); super.onDestroy() }
}
