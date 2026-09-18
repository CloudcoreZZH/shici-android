package io.github.shici.app.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.BackEventCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.shici.core.SessionMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ShiciApp(state: AppState, model: AppViewModel, speak: (String) -> Unit) {
    val snackbars = remember { SnackbarHostState() }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backDirection by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(model) { for (message in model.messages) snackbars.showSnackbar(message) }
    val waitingUntil = state.session?.current?.availableAt
    LaunchedEffect(waitingUntil) {
        if (waitingUntil != null && waitingUntil.isAfter(state.now)) {
            delay(Duration.between(Instant.now(), waitingUntil).toMillis().coerceAtLeast(1))
            model.refresh()
        }
    }
    PredictiveBackHandler(state.canGoBack) { events ->
        try { events.collect {
            backProgress = it.progress
            backDirection = if (it.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
        }; model.goBack() }
        catch (_: CancellationException) { /* Cancelled gestures restore the current screen. */ }
        finally { backProgress = 0f }
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Scaffold(containerColor = Color.Transparent, contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(snackbars) },
            bottomBar = {
                if (state.session == null && !state.reviewOverview && state.detail == null && !state.loading) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)) {
                        val icons = listOf(Icons.Outlined.Search, Icons.AutoMirrored.Outlined.MenuBook, Icons.AutoMirrored.Outlined.LibraryBooks, Icons.Outlined.PersonOutline)
                        Tab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(state.tab == tab, { model.changeTab(tab) },
                                icon = { Icon(icons[index], null) }, label = { Text(tab.label) })
                        }
                    }
                }
            }) { padding ->
            Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize().graphicsLayer {
                scaleX = 1 - backProgress * 0.06f; scaleY = 1 - backProgress * 0.06f
                translationX = backProgress * 32.dp.toPx() * backDirection
            }, contentAlignment = Alignment.TopCenter) {
                AnimatedContent(targetState = state, contentKey = ::screenKey,
                    modifier = Modifier.widthIn(max = 840.dp).fillMaxSize(),
                    transitionSpec = { (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 16 }) togetherWith fadeOut(tween(120)) },
                    label = "screen") { state ->
                    Box(Modifier.fillMaxSize()) {
                    when {
                        state.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(); Spacer(Modifier.height(20.dp)); Text("正在准备离线词典…")
                        }
                        state.fatalError != null -> EmptyState("准备未完成", state.fatalError, "重试") { model.refresh() }
                        state.session != null -> StudyScreen(state, speak, model::goBack, model::reveal, model::answer, model::undo,
                            { model.start(state.session.mode) }, { model.start(SessionMode.LEARN, fresh = true) })
                        state.detail != null -> DictionaryScreen(state.detail,
                            state.snapshot?.words?.find { it.word == state.detail.word }?.additionCount ?: 0,
                            state.snapshot?.book?.name.orEmpty(), speak, model::goBack, model::addCurrentWord,
                            state.adding, { model.start(SessionMode.LEARN) })
                        state.reviewOverview -> ReviewOverview(state, model::goBack) { model.start(SessionMode.REVIEW) }
                        state.tab == Tab.SEARCH -> SearchScreen(state, model::search) { model.openWord(it) }
                        state.tab == Tab.HOME -> HomeScreen(state, { model.selectBook(it) }, { model.createBook(it) },
                            { model.start(SessionMode.LEARN) }, { model.showReview() }, { model.changeTab(Tab.SEARCH) })
                        state.tab == Tab.BOOK -> BookScreen(state, { model.selectBook(it) }, { model.createBook(it) },
                            { model.openWord(it) }, { model.removeWord(it) }, { model.start(SessionMode.LEARN) }, { model.showReview() })
                        else -> SettingsScreen(state, model::setAppearance, model::setRetention, model::setGroupSize) { model.reset() }
                    }
                    }
                }
            }
        }
    }
}

private fun screenKey(state: AppState): String = when {
    state.loading -> "loading"
    state.fatalError != null -> "error"
    state.session != null -> "session"
    state.detail != null -> "word:${state.detail.word}"
    state.reviewOverview -> "review"
    else -> state.tab.name
}
