package io.github.shici.app.data

import io.github.shici.core.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Only structured state is persisted; answers are deliberately hidden when a group is resumed. */
internal object StudyCodec {
    fun memory(state: MemoryState): String = JSONObject().apply {
        put("stability", state.stability); put("difficulty", state.difficulty)
        put("reviewed", state.lastReviewedAt.toEpochMilli()); put("due", state.dueAt.toEpochMilli())
        put("phase", state.phase.name); put("step", state.step)
        put("repetitions", state.repetitions); put("lapses", state.lapses)
    }.toString()

    fun memory(text: String): MemoryState = JSONObject(text).let { value ->
        MemoryState(value.getDouble("stability"), value.getDouble("difficulty"),
            Instant.ofEpochMilli(value.getLong("reviewed")), Instant.ofEpochMilli(value.getLong("due")),
            Phase.valueOf(value.getString("phase")), value.getInt("step"), value.getInt("repetitions"), value.getInt("lapses"))
    }

    fun encode(progress: StudyProgress): String = JSONObject().apply {
        put("id", progress.id); put("book", progress.bookId); put("mode", progress.mode.name)
        put("total", progress.total); put("completed", progress.completed); put("answers", progress.answers)
        put("forgotten", progress.forgotten); put("started", progress.startedAt.toEpochMilli())
        put("finished", progress.finishedAt?.toEpochMilli()); put("lastAction", progress.lastActionId)
        put("items", JSONArray().apply { progress.items.forEach { item -> put(JSONObject().apply {
            put("word", item.word); put("task", item.taskId); put("version", item.memoryVersion?.toEpochMilli())
            put("available", item.availableAt.toEpochMilli())
        }) } })
    }.toString()

    fun decode(text: String): StudyProgress = JSONObject(text).let { value ->
        val items = value.getJSONArray("items")
        StudyProgress(value.getString("id"), value.getLong("book"), SessionMode.valueOf(value.getString("mode")),
            List(items.length()) { index -> items.getJSONObject(index).let { item ->
                StudyItem(item.getString("word"), item.stringOrNull("task"), item.instantOrNull("version"),
                    Instant.ofEpochMilli(item.getLong("available")))
            } }, value.getInt("total"), value.getInt("completed"), value.getInt("answers"), value.getInt("forgotten"),
            Instant.ofEpochMilli(value.getLong("started")), value.instantOrNull("finished"), value.stringOrNull("lastAction"))
    }

    private fun JSONObject.stringOrNull(key: String) = if (isNull(key)) null else getString(key)
    private fun JSONObject.instantOrNull(key: String) = if (isNull(key)) null else Instant.ofEpochMilli(getLong(key))
}
