package io.github.shici.app.ui

import androidx.activity.compose.PredictiveBackHandler
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun ShiciApp(state: AppState, model: AppViewModel, speak: (String) -> Unit) {
    val snackbars = remember { SnackbarHostState() }
    var backProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(model) { for (message in model.messages) snackbars.showSnackbar(message) }
    PredictiveBackHandler(state.canGoBack && state.additionResult == null) { events ->
        try { events.collect { backProgress = it.progress }; model.goBack() }
        catch (_: CancellationException) { /* Cancelled gestures restore the current screen. */ }
        finally { backProgress = 0f }
    }
    val scenic = !state.loading && state.fatalError == null && state.detail == null && !state.reviewOverview &&
        (state.session != null || state.tab == Tab.HOME)
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (scenic) MountainWallpaper(isDark(state.appearance))
        Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbars) },
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
            Box(Modifier.padding(padding).fillMaxSize().graphicsLayer {
                scaleX = 1 - backProgress * 0.06f; scaleY = 1 - backProgress * 0.06f
                translationX = backProgress * 32.dp.toPx()
            }, contentAlignment = Alignment.TopCenter) {
                AnimatedContent(targetState = state, contentKey = ::screenKey,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                    transitionSpec = { (fadeIn(tween(180)) + slideInHorizontally(tween(180)) { it / 16 }) togetherWith fadeOut(tween(120)) },
                    label = "screen") { state ->
                    Box(Modifier.fillMaxSize()) {
                    when {
                        state.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(); Spacer(Modifier.height(20.dp)); Text("正在准备离线词典…")
                        }
                        state.fatalError != null -> EmptyState("准备未完成", state.fatalError, "重试") { model.refresh() }
                        state.session != null -> StudyScreen(state, speak, model::goBack, model::reveal, model::answer)
                        state.detail != null -> DictionaryScreen(state.detail,
                            state.snapshot?.words?.find { it.word == state.detail.word }?.additionCount ?: 0,
                            state.snapshot?.book?.name.orEmpty(), speak, model::goBack, model::addCurrentWord)
                        state.reviewOverview -> ReviewOverview(state, model::goBack) { model.start(SessionMode.REVIEW) }
                        state.tab == Tab.SEARCH -> SearchScreen(state, model::search) { model.openWord(it) }
                        state.tab == Tab.HOME -> HomeScreen(state, { model.selectBook(it) }, { model.createBook(it) },
                            { model.start(SessionMode.LEARN) }, { model.showReview() }, { model.openWord(it) })
                        state.tab == Tab.BOOK -> BookScreen(state, { model.selectBook(it) }, { model.createBook(it) },
                            { model.openWord(it) }, { model.removeWord(it) }, { model.start(SessionMode.LEARN) })
                        else -> SettingsScreen(state, model::setAppearance, model::setRetention) { model.reset() }
                    }
                    }
                }
            }
        }
    }
    state.additionResult?.let { (word, count) ->
        ModalBottomSheet(onDismissRequest = model::dismissAdded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text("已加入词书", style = MaterialTheme.typography.titleLarge)
                Text(word, fontSize = 32.sp)
                Text(state.snapshot?.book?.name.orEmpty())
                Text("累计加入 $count 次", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary)
                Text("新增 1 次重学任务 · 复习时优先安排")
                AccentButton("去学习", { model.start(SessionMode.LEARN) }, Modifier.fillMaxWidth())
                TextButton(model::dismissAdded) { Text("继续查词") }
                QuietText("每次加入，都会重新学一次")
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
