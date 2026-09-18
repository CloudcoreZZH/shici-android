package io.github.shici.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.shici.core.Sense
import io.github.shici.core.WordEntry
import io.github.shici.core.normalizeWord
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/** Immutable dictionary has its own database. It never opens or migrates personal progress. */
class DictionaryStore(private val context: Context) {
    private val editorial by lazy { EditorialNotes(context) }
    private val references by lazy { ReferenceDictionary(context) }
    val editorialSize get() = editorial.size
    val referenceSize get() = references.count("accepted_words")
    val baseSize get() = references.count("base_words")
    private val database: SQLiteDatabase by lazy {
        val destination = File(context.noBackupFilesDir, "dictionary-v1.db")
        if (!destination.exists()) {
            val staging = File(context.noBackupFilesDir, "dictionary-v1.partial")
            context.assets.open("dictionary.db").use { source ->
                staging.outputStream().use { output -> source.copyTo(output) }
            }
            check(staging.renameTo(destination)) { "词典准备失败，请检查剩余存储空间。" }
        }
        SQLiteDatabase.openDatabase(destination.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    @Synchronized fun search(query: String, limit: Int = 30): List<WordEntry> {
        val prefix = normalizeWord(query)
        if (prefix.isEmpty()) return emptyList()
        // A range scan uses the primary key, unlike an unindexed leading wildcard LIKE.
        val maximum = limit.coerceIn(1, 100)
        val base = database.rawQuery(
            "SELECT * FROM words WHERE word >= ? AND word < ? ORDER BY word LIMIT ?",
            arrayOf(prefix, prefix + '\uffff', maximum.toString())
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) add(readEntry(cursor))
        } }
        val baseKeys = base.map { it.word }.toSet()
        val supplemental = references.searchKeys(prefix, maximum).filter { it !in baseKeys }
            .mapNotNull { references.fallback(it) }
        return (base + supplemental).sortedBy { it.word }.take(maximum)
    }

    @Synchronized fun find(word: String): WordEntry? {
        val key = normalizeWord(word)
        val base = database.rawQuery("SELECT * FROM words WHERE word=?", arrayOf(key))
            .use { if (it.moveToFirst()) readEntry(it) else null }
        return base?.copy(references = references.references(key), inNetem2024 = references.containsNetem2024(key))
            ?: references.fallback(key)?.let(editorial::apply)
    }

    @Synchronized fun size(): Int = references.count("searchable_words")

    @Synchronized fun close() { database.close(); references.close() }

    private fun readEntry(cursor: android.database.Cursor): WordEntry {
        fun text(name: String) = cursor.getString(cursor.getColumnIndexOrThrow(name))
        val word = text("word")
        val stats = database.rawQuery("SELECT sense_id,count,source FROM exam_senses WHERE word=?", arrayOf(word))
            .use { rows -> buildMap {
                while (rows.moveToNext()) put(rows.getString(0), rows.getInt(1) to rows.getString(2))
            } }
        val senses = text("translation").lineSequence().filter { it.isNotBlank() }.map { line ->
            val id = MessageDigest.getInstance("SHA-256").digest((word + "\n" + line).toByteArray(Charsets.UTF_8))
                .take(12).joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 255) }
            Sense(id, line, stats[id]?.first, stats[id]?.second)
        }.toList()
        return editorial.apply(WordEntry(word, text("phonetic"), senses, text("definition"),
            text("tags").split(' ').filter { it.isNotBlank() }.toSet(), text("exchange")))
    }
}
