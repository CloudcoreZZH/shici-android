package io.github.shici.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import io.github.shici.core.DictionaryReference
import io.github.shici.core.Sense
import io.github.shici.core.WordEntry
import java.io.File

/** Community definitions and a historical checklist; never an exam-priority authority. */
internal class ReferenceDictionary(private val context: Context) {
    private val database by lazy {
        val destination = File(context.noBackupFilesDir, "references-v1.db")
        if (!destination.exists()) {
            val staging = File(context.noBackupFilesDir, "references-v1.partial")
            context.assets.open("references.db").use { source ->
                staging.outputStream().use { source.copyTo(it) }
            }
            check(staging.renameTo(destination)) { "补充词典准备失败，请检查剩余存储空间。" }
        }
        SQLiteDatabase.openDatabase(destination.path, null, SQLiteDatabase.OPEN_READONLY)
    }

    fun count(key: String): Int = database.rawQuery("SELECT value FROM metadata WHERE key=?", arrayOf(key))
        .use { if (it.moveToFirst()) it.getString(0).toInt() else 0 }

    fun containsNetem2024(word: String): Boolean = database.rawQuery(
        "SELECT 1 FROM netem_2024 WHERE word=?", arrayOf(word)
    ).use { it.moveToFirst() }

    fun references(word: String): List<DictionaryReference> = database.rawQuery(
        "SELECT headword,pos,gloss,labels FROM definitions WHERE word=? ORDER BY ordinal", arrayOf(word)
    ).use { rows ->
        val byHeadword = linkedMapOf<String, MutableList<String>>()
        while (rows.moveToNext()) {
            val labels = rows.getString(3).takeIf { it.isNotBlank() }?.let { "〔$it〕" }.orEmpty()
            byHeadword.getOrPut(rows.getString(0)) { mutableListOf() }
                .add("${rows.getString(1)} · $labels${rows.getString(2)}")
        }
        byHeadword.map { (headword, senses) ->
            DictionaryReference(headword, "https://zh.wiktionary.org/wiki/${Uri.encode(headword)}", senses.distinct())
        }
    }

    fun fallback(word: String): WordEntry? {
        val references = references(word)
        if (references.isEmpty()) return null
        return WordEntry(word, "", references.flatMap { it.senses }.distinct().mapIndexed { index, text ->
            Sense("wiki-$word-$index", text)
        }, source = "中文维基词典", references = references, inNetem2024 = containsNetem2024(word))
    }

    fun searchKeys(prefix: String, limit: Int): List<String> = database.rawQuery(
        "SELECT DISTINCT word FROM definitions WHERE word>=? AND word<? ORDER BY word LIMIT ?",
        arrayOf(prefix, prefix + '\uffff', limit.toString())
    ).use { rows -> buildList { while (rows.moveToNext()) add(rows.getString(0)) } }

    fun close() = database.close()
}
