package io.github.shici.app.ui

import io.github.shici.core.*
import java.time.Instant

enum class Tab(val label: String) { SEARCH("查词"), HOME("学习"), BOOK("词书"), SETTINGS("我的") }
enum class Appearance(val label: String) { SYSTEM("跟随系统"), LIGHT("浅色"), DARK("深色") }
data class StudySession(
    val progress: StudyProgress,
    val entry: WordEntry? = null,
    val revealed: Boolean = false,
    val saving: Boolean = false,
) {
    val current get() = progress.items.firstOrNull()
    val mode get() = progress.mode
    val finished get() = progress.finished
}
data class AppState(
    val loading: Boolean = true,
    val fatalError: String? = null,
    val tab: Tab = Tab.HOME,
    val query: String = "",
    val searching: Boolean = false,
    val searchResults: List<WordEntry> = emptyList(),
    val detail: WordEntry? = null,
    val books: List<WordBook> = emptyList(),
    val snapshot: BookSnapshot? = null,
    val meanings: Map<String, String> = emptyMap(),
    val dictionarySize: Int = 0,
    val editorialSize: Int = 0,
    val adding: Boolean = false,
    val session: StudySession? = null,
    val reviewOverview: Boolean = false,
    val appearance: Appearance = Appearance.SYSTEM,
    val retention: Double = 0.9,
    val groupSize: Int = 10,
    val savedSessions: Map<SessionMode, StudyProgress> = emptyMap(),
    val now: Instant = Instant.now(),
) {
    val dueWords get() = reviewQueue(snapshot?.words.orEmpty(), now)
    val canGoBack get() = detail != null || session != null || reviewOverview
    val nextReviewAt get() = snapshot?.words.orEmpty().filter { it.pendingCount == 0 }
        .mapNotNull { it.memory?.dueAt }.filter { it.isAfter(now) }.minOrNull()
}
