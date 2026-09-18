package io.github.shici.core

import java.time.Instant

/** A bounded practice group. A failed learning attempt does not consume an addition. */
data class StudyProgress(
    val id: String,
    val bookId: Long,
    val mode: SessionMode,
    val items: List<StudyItem>,
    val total: Int,
    val completed: Int = 0,
    val answers: Int = 0,
    val forgotten: Int = 0,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val lastActionId: String? = null,
) {
    val finished get() = items.isEmpty()
    fun ordered(now: Instant) = copy(items = items.sortedWith(
        compareBy<StudyItem> { it.availableAt.isAfter(now) }
            .thenBy { if (it.availableAt.isAfter(now)) it.availableAt else Instant.EPOCH }
    ))

    fun afterAnswer(rating: Rating, nextMemory: MemoryState, now: Instant, actionId: String): StudyProgress {
        val current = items.first()
        require(!current.availableAt.isAfter(now))
        val retry = mode == SessionMode.LEARN && rating == Rating.AGAIN
        val remaining = items.drop(1) + if (retry) listOf(current.copy(availableAt = nextMemory.dueAt)) else emptyList()
        return copy(items = remaining, completed = completed + if (retry) 0 else 1,
            answers = answers + 1, forgotten = forgotten + if (rating == Rating.AGAIN) 1 else 0,
            finishedAt = now.takeIf { remaining.isEmpty() }, lastActionId = actionId).ordered(now)
    }
}

/** Multiple additions stay independent, but a word appears only once in each new group. */
fun learningGroup(tasks: List<LearningTask>, limit: Int, now: Instant): List<StudyItem> {
    require(limit in 1..50)
    return tasks.sortedBy { it.addedAt }.distinctBy { it.word }
        .sortedWith(compareBy<LearningTask> { it.availableAt.isAfter(now) }
            .thenBy { if (it.availableAt.isAfter(now)) it.availableAt else Instant.EPOCH })
        .take(limit).map { StudyItem(it.word, taskId = it.id, availableAt = it.availableAt) }
}
