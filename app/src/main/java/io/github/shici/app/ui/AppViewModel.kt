package io.github.shici.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import io.github.shici.app.ShiciApplication
import io.github.shici.core.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val container = application as ShiciApplication
    private val repository = container.learning
    private val dictionary = container.dictionary
    private val preferences = application.getSharedPreferences("settings", 0)
    private val mutations = Mutex()
    private var searchJob: Job? = null
    private val meaningCache = mutableMapOf<String, String>()
    private val mutable = MutableStateFlow(AppState(
        tab = runCatching { Tab.valueOf(saved.get<String>("tab") ?: "HOME") }.getOrDefault(Tab.HOME),
        appearance = runCatching { Appearance.valueOf(preferences.getString("appearance", "SYSTEM")!!) }.getOrDefault(Appearance.SYSTEM),
        retention = preferences.getFloat("retention", 0.9f).toDouble().coerceIn(0.7, 0.97),
        groupSize = preferences.getInt("groupSize", 10).coerceIn(5, 20),
    ))
    val state: StateFlow<AppState> = mutable.asStateFlow()
    val messages = Channel<String>(Channel.BUFFERED)
    private var bookId = preferences.getLong("book", 1)

    init { refresh() }

    fun refresh() = launchSafely {
        mutations.withLock { refreshData() }
    }

    private suspend fun refreshData() {
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        val result = withContext(Dispatchers.IO) {
            val books = repository.books()
            if (books.none { it.id == bookId }) bookId = books.first().id
            val snapshot = repository.snapshot(bookId, now)
            val meanings = snapshot.words.associate { word -> word.word to meaningCache.getOrPut(word.word) {
                dictionary.find(word.word)?.learningSenses()?.firstOrNull()?.text.orEmpty()
            } }
            val sessions = SessionMode.entries.mapNotNull { mode ->
                repository.session(bookId, mode)?.takeUnless { it.finished }?.let { mode to it }
            }.toMap()
            DashboardData(books, snapshot, meanings, dictionary.size(), dictionary.editorialSize, sessions,
                dictionary.baseSize, dictionary.referenceSize)
        }
        mutable.update { it.copy(loading = false, fatalError = null, now = now, books = result.books,
            snapshot = result.snapshot, meanings = result.meanings, dictionarySize = result.dictionarySize,
            editorialSize = result.editorialSize, savedSessions = result.sessions,
            baseDictionarySize = result.baseSize, referenceSize = result.referenceSize) }
    }

    fun changeTab(tab: Tab) {
        saved["tab"] = tab.name
        mutable.update { it.copy(tab = tab, detail = null, reviewOverview = false) }
    }
    fun search(query: String) {
        searchJob?.cancel()
        mutable.update { it.copy(query = query.take(100), searching = query.isNotBlank(),
            searchResults = if (query.isBlank()) emptyList() else it.searchResults) }
        searchJob = launchSafely {
            delay(180)
            val results = withContext(Dispatchers.IO) { dictionary.search(query.take(100)) }
            mutable.update { it.copy(searchResults = results, searching = false) }
        }
    }

    fun openWord(word: String) = launchSafely {
        val entry = withContext(Dispatchers.IO) { dictionary.find(word) }
        if (entry == null) messages.send("词典暂未收录这个词。") else mutable.update { it.copy(detail = entry) }
    }

    fun addCurrentWord() {
        if (state.value.adding) return
        val word = state.value.detail?.word ?: return
        val targetBook = bookId
        val action = UUID.randomUUID().toString()
        mutable.update { it.copy(adding = true) }
        launchSafely {
            try {
            mutations.withLock {
                withContext(Dispatchers.IO) { repository.add(targetBook, word, action, Instant.now()) }
                refreshData()
            }
            } finally { mutable.update { it.copy(adding = false) } }
        }
    }

    fun selectBook(id: Long) = launchSafely {
        mutations.withLock {
            bookId = id
            preferences.edit { putLong("book", id) }
            refreshData()
        }
    }

    fun createBook(name: String) = launchSafely {
        mutations.withLock {
            bookId = withContext(Dispatchers.IO) { repository.createBook(name) }
            preferences.edit { putLong("book", bookId) }
            refreshData()
        }
    }

    fun removeWord(word: String) = launchSafely {
        mutations.withLock {
            withContext(Dispatchers.IO) { repository.removeWord(bookId, word) }
            refreshData()
        }
    }

    fun showReview() = launchSafely {
        mutations.withLock { refreshData(); mutable.update { it.copy(detail = null, reviewOverview = true) } }
    }

    fun start(mode: SessionMode, fresh: Boolean = false) = launchSafely {
        mutations.withLock {
            refreshData()
            val progress = withContext(Dispatchers.IO) { repository.beginSession(bookId, mode, state.value.groupSize, state.value.now, fresh) }
            if (progress == null) {
                refreshData()
                messages.send(if (mode == SessionMode.LEARN) "暂时没有待学词，先查一个词加入词书吧。" else "当前没有到期词，稍后再来。")
                return@withLock
            }
            val session = loadSession(progress)
            mutable.update { it.copy(session = session, detail = null,
                reviewOverview = false, savedSessions = it.savedSessions + (mode to progress)) }
        }
    }

    fun reveal() {
        val session = state.value.session ?: return
        if (session.current?.availableAt?.isAfter(state.value.now) == true || session.saving) return
        mutable.update { it.copy(session = it.session?.copy(revealed = true)) }
    }

    fun answer(rating: Rating) {
        val session = state.value.session ?: return
        val item = session.current ?: return
        if (!session.revealed || session.saving || session.finished) return
        mutable.update { it.copy(session = session.copy(saving = true)) }
        val actionId = UUID.randomUUID().toString()
        launchSafely {
            try {
                mutations.withLock {
                    val progress = withContext(Dispatchers.IO) {
                        repository.answerSession(session.progress.bookId, session.mode, session.progress.id, item, rating, actionId,
                            Instant.now().truncatedTo(ChronoUnit.MILLIS), state.value.retention)
                    }
                    val next = loadSession(progress)
                    mutable.update { current ->
                        // Back can be pressed during an I/O operation; never reopen an exited session.
                        if (current.session?.progress?.id != session.progress.id) current else current.copy(session = next)
                    }
                    refreshData()
                }
            } finally {
                mutable.update { it.copy(session = it.session?.copy(saving = false)) }
            }
        }
    }

    fun undo() {
        val session = state.value.session ?: return
        val action = session.progress.lastActionId ?: return
        if (session.saving) return
        mutable.update { it.copy(session = session.copy(saving = true)) }
        launchSafely {
            try {
                mutations.withLock {
                    val progress = withContext(Dispatchers.IO) { repository.undoAnswer(session.progress.bookId, session.mode, action) }
                    val restored = loadSession(progress.ordered(Instant.now()))
                    mutable.update { if (it.session?.progress?.id == session.progress.id) it.copy(session = restored) else it }
                    refreshData()
                }
            } finally { mutable.update { it.copy(session = it.session?.copy(saving = false)) } }
        }
    }

    private suspend fun loadSession(progress: StudyProgress): StudySession {
        val entry = progress.items.firstOrNull()?.let { item -> withContext(Dispatchers.IO) {
            dictionary.find(item.word) ?: error("当前词典缺少待学词，请保留学习数据并更新词典。")
        } }
        return StudySession(progress, entry)
    }

    fun setGroupSize(value: Int) {
        require(value in listOf(5, 10, 20))
        preferences.edit { putInt("groupSize", value) }
        mutable.update { it.copy(groupSize = value) }
    }

    fun goBack() {
        mutable.update { when {
            it.session != null -> it.copy(session = null)
            it.detail != null -> it.copy(detail = null)
            else -> it.copy(reviewOverview = false)
        } }
    }

    fun setAppearance(value: Appearance) {
        preferences.edit { putString("appearance", value.name) }
        mutable.update { it.copy(appearance = value) }
    }

    fun setRetention(value: Double) {
        require(value in 0.70..0.97)
        preferences.edit { putFloat("retention", value.toFloat()) }
        mutable.update { it.copy(retention = value) }
    }

    fun reset() = launchSafely {
        mutations.withLock {
            withContext(Dispatchers.IO) { repository.reset() }
            preferences.edit { clear() }
            bookId = 1
            mutable.value = AppState()
            refreshData()
            messages.send("个人学习数据与设置已清除。")
        }
    }

    private fun launchSafely(block: suspend CoroutineScope.() -> Unit): Job = viewModelScope.launch {
        try { block() } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) {
            val message = failure.message ?: "操作未完成，请重试。"
            if (state.value.loading) mutable.update { it.copy(loading = false, fatalError = message) }
            else messages.send(message)
            mutable.update { it.copy(searching = false) }
        }
    }
}

private data class DashboardData(val books: List<WordBook>, val snapshot: BookSnapshot,
    val meanings: Map<String, String>, val dictionarySize: Int, val editorialSize: Int,
    val sessions: Map<SessionMode, StudyProgress>, val baseSize: Int, val referenceSize: Int)
