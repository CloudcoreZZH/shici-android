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
    private val mutable = MutableStateFlow(AppState(
        tab = runCatching { Tab.valueOf(saved.get<String>("tab") ?: "HOME") }.getOrDefault(Tab.HOME),
        appearance = runCatching { Appearance.valueOf(preferences.getString("appearance", "SYSTEM")!!) }.getOrDefault(Appearance.SYSTEM),
        retention = preferences.getFloat("retention", 0.9f).toDouble().coerceIn(0.7, 0.97),
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
            val meanings = snapshot.words.associate { it.word to dictionary.find(it.word)?.senses?.firstOrNull()?.text.orEmpty() }
            Triple(books, snapshot, meanings) to dictionary.size()
        }
        mutable.update { it.copy(loading = false, fatalError = null, now = now, books = result.first.first,
            snapshot = result.first.second, meanings = result.first.third, dictionarySize = result.second) }
    }

    fun changeTab(tab: Tab) {
        saved["tab"] = tab.name
        mutable.update { it.copy(tab = tab, detail = null, reviewOverview = false) }
    }
    fun search(query: String) {
        searchJob?.cancel()
        mutable.update { it.copy(query = query.take(100), searching = query.isNotBlank()) }
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
        val word = state.value.detail?.word ?: return
        val targetBook = bookId
        val action = UUID.randomUUID().toString()
        launchSafely {
            mutations.withLock {
                withContext(Dispatchers.IO) { repository.add(targetBook, word, action, Instant.now()) }
                refreshData()
                val count = state.value.snapshot?.words?.find { it.word == word }?.additionCount ?: 1
                mutable.update { it.copy(additionResult = word to count) }
            }
        }
    }

    fun dismissAdded() { mutable.update { it.copy(additionResult = null) } }
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

    fun start(mode: SessionMode) = launchSafely {
        mutations.withLock {
            refreshData()
            val items = if (mode == SessionMode.LEARN) withContext(Dispatchers.IO) {
                repository.pendingTasks(bookId).map { StudyItem(it.word, taskId = it.id) }
            } else state.value.dueWords.map { StudyItem(it.word, memoryVersion = it.memory!!.lastReviewedAt) }
            if (items.isEmpty()) {
                messages.send(if (mode == SessionMode.LEARN) "暂时没有待学词，先查一个词加入词书吧。" else "当前没有到期词，稍后再来。")
                return@withLock
            }
            val entry = withContext(Dispatchers.IO) { dictionary.find(items.first().word) }
                ?: error("当前词典缺少待学词，请保留学习数据并更新词典。")
            mutable.update { it.copy(session = StudySession(bookId, mode, items, entry = entry), detail = null,
                additionResult = null, reviewOverview = false) }
        }
    }

    fun reveal(rating: Rating = Rating.GOOD) {
        mutable.update { it.copy(session = it.session?.copy(revealed = true, tentativeRating = rating)) }
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
                    val accepted = withContext(Dispatchers.IO) {
                        repository.answer(session.bookId, item, session.mode, rating, actionId,
                            Instant.now().truncatedTo(ChronoUnit.MILLIS), state.value.retention)
                    }
                    if (!accepted) messages.send("这项任务已更新，本次没有重复记录。")
                    val nextIndex = session.index + 1
                    val nextEntry = session.items.getOrNull(nextIndex)?.let { next ->
                        withContext(Dispatchers.IO) { dictionary.find(next.word) }
                    }
                    mutable.update { current ->
                        // Back can be pressed during an I/O operation; never reopen an exited session.
                        if (current.session?.items !== session.items) current else current.copy(session = session.copy(
                            index = nextIndex, entry = nextEntry, revealed = false, saving = false,
                            finished = nextIndex == session.items.size,
                        ))
                    }
                    refreshData()
                }
            } finally {
                mutable.update { it.copy(session = it.session?.copy(saving = false)) }
            }
        }
    }

    fun goBack() {
        mutable.update { when {
            it.additionResult != null -> it.copy(additionResult = null)
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
