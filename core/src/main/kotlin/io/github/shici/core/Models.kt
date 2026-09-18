package io.github.shici.core

import java.time.Instant
import java.text.Normalizer
import java.util.Locale

fun normalizeWord(value: String): String =
    Normalizer.normalize(value.trim(), Normalizer.Form.NFKC).lowercase(Locale.ROOT)

data class Sense(val id: String, val text: String, val examCount: Int? = null, val examSource: String? = null)
data class DictionaryReference(val headword: String, val url: String, val senses: List<String>)
data class WordEntry(
    val word: String,
    val phonetic: String,
    val senses: List<Sense>,
    val english: String = "",
    val tags: Set<String> = emptySet(),
    val exchange: String = "",
    val source: String = "ECDICT",
    val editorialSenses: List<Sense> = emptyList(),
    val example: String = "",
    val exampleTranslation: String = "",
    val references: List<DictionaryReference> = emptyList(),
    val inNetem2024: Boolean = false,
) {
    val hasExamStatistics get() = senses.any { it.examCount != null && !it.examSource.isNullOrBlank() }
    fun examSenses(): List<Sense> = senses.sortedWith(
        compareByDescending<Sense> { if (it.examSource.isNullOrBlank()) null else it.examCount }
    )
    fun learningSenses(): List<Sense> = when {
        hasExamStatistics -> examSenses()
        editorialSenses.isNotEmpty() -> editorialSenses
        else -> senses
    }
    val senseLabel get() = when {
        hasExamStatistics -> "考研义项统计"
        editorialSenses.isNotEmpty() -> "考研释义优先级"
        else -> "完整词典释义"
    }
}

enum class Rating(val value: Int, val label: String) {
    AGAIN(1, "忘记"), HARD(2, "困难"), GOOD(3, "记得"), EASY(4, "轻松")
}
enum class Phase { LEARNING, REVIEW, RELEARNING }
data class MemoryState(
    val stability: Double,
    val difficulty: Double,
    val lastReviewedAt: Instant,
    val dueAt: Instant,
    val phase: Phase,
    val step: Int = 0,
    val repetitions: Int = 1,
    val lapses: Int = 0,
) {
    init {
        require(stability.isFinite() && stability >= 0.001)
        require(difficulty.isFinite() && difficulty in 1.0..10.0)
        require(step >= 0 && repetitions >= 1 && lapses >= 0)
    }
}
data class WordBook(val id: Long, val name: String)
data class BookWord(
    val word: String,
    val additionCount: Int,
    val pendingCount: Int,
    val lastAddedAt: Instant,
    val memory: MemoryState?,
) {
    fun isDue(now: Instant) = pendingCount == 0 && memory != null && !memory.dueAt.isAfter(now)
}
data class LearningTask(val id: String, val bookId: Long, val word: String, val addedAt: Instant,
                        val availableAt: Instant = Instant.EPOCH)
data class StudyItem(val word: String, val taskId: String? = null, val memoryVersion: Instant? = null,
                     val availableAt: Instant = Instant.EPOCH)
enum class SessionMode { LEARN, REVIEW }
data class BookSnapshot(
    val book: WordBook,
    val words: List<BookWord>,
    val completedToday: Int,
    val reviewedToday: Int = 0,
    val readyLearningWords: Int = 0,
) {
    val pendingCount get() = words.sumOf { it.pendingCount }
    val totalAdditions get() = words.sumOf { it.additionCount }
}

/** Counts determine priority only among due cards. Early manual re-additions remain learning tasks. */
fun reviewQueue(words: List<BookWord>, now: Instant): List<BookWord> = words.filter { it.isDue(now) }
    .sortedWith(compareByDescending<BookWord> { it.additionCount }
        .thenBy { it.memory!!.dueAt }
        .thenBy { it.word })
