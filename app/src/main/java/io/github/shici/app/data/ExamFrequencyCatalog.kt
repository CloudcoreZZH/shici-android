package io.github.shici.app.data

import android.content.Context
import io.github.shici.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Bundled, reviewable evidence only. No inferred counts, network calls or legacy grouped-line counts. */
internal class ExamFrequencyCatalog private constructor(root: JSONObject) {
    constructor(context: Context) : this(context.assets.open("exam-frequency.json").bufferedReader().use { JSONObject(it.readText()) })
    internal constructor(json: String) : this(JSONObject(json))
    private val entries: Map<String, ExamStatistics>
    val size get() = entries.size

    init {
        require(root.getInt("schemaVersion") == 1)
        val corpora = root.getJSONArray("corpora").objects().map { value ->
            ExamCorpus(value.getString("id"), value.getString("title"), value.getString("exam"),
                value.getJSONArray("papers").objects().map { ExamPaper(it.getString("id"), it.getInt("year"), it.getString("sourceUrl")) },
                value.getString("methodologyUrl"), value.getString("license"))
        }
        require(corpora.map { it.id }.distinct().size == corpora.size)
        val records = root.getJSONArray("words").objects().map { value ->
            require(value.getBoolean("reviewed"))
            val word = value.getString("word")
            require(word.isNotBlank() && normalizeWord(word) == word)
            val corpus = corpora.single { it.id == value.getString("corpusId") }
            val senses = value.getJSONArray("senses").objects().map { sense ->
                CountedSense(sense.getString("id"), sense.getString("text"),
                    if (sense.isNull("occurrences")) null else sense.getJSONArray("occurrences").objects().map {
                        ExamOccurrence(it.getString("paperId"), it.getString("location"), it.getString("reviewedBy"))
                    }, sense.optString("reviewedBy").takeIf { it.isNotBlank() })
            }
            word to ExamStatistics(corpus, senses)
        }
        require(records.map { it.first }.distinct().size == records.size)
        entries = records.toMap()
    }

    fun apply(entry: WordEntry) = entry.copy(examStatistics = entries[normalizeWord(entry.word)])
}

private fun JSONArray.objects() = (0 until length()).map(::getJSONObject)
