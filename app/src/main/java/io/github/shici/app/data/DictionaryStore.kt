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
    val editorialSize get() = editorial.size
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
        return database.rawQuery(
            "SELECT * FROM words WHERE word >= ? AND word < ? ORDER BY word LIMIT ?",
            arrayOf(prefix, prefix + '\uffff', limit.coerceIn(1, 100).toString())
        ).use { cursor -> buildList {
            while (cursor.moveToNext()) add(readEntry(cursor))
        } }
    }

    @Synchronized fun find(word: String): WordEntry? = database.rawQuery(
        "SELECT * FROM words WHERE word=?", arrayOf(normalizeWord(word))
    ).use { if (it.moveToFirst()) readEntry(it) else null }

    @Synchronized fun size(): Int = database.rawQuery("SELECT value FROM metadata WHERE key='entries'", null)
        .use { if (it.moveToFirst()) it.getString(0).toInt() else 0 }

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
