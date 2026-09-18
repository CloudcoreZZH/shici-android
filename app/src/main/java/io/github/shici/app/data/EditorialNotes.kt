package io.github.shici.app.data

import android.content.Context
import io.github.shici.core.Sense
import io.github.shici.core.WordEntry

/** Authored reading notes, never presented as measured exam frequencies. */
class EditorialNotes(context: Context) {
    private data class Note(val senses: List<Sense>, val example: String, val translation: String)
    private val notes = context.assets.open("editorial-notes.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
        lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
            val fields = line.split('\t')
            check(fields.size == 4) { "释义资料格式有误。" }
            val word = fields[0]
            word to Note(fields[1].split('|').mapIndexed { index, text -> Sense("editorial:$word:$index", text) }, fields[2], fields[3])
        }
    }
    val size get() = notes.size
    fun apply(entry: WordEntry): WordEntry = notes[entry.word]?.let { note ->
        entry.copy(editorialSenses = note.senses, example = note.example, exampleTranslation = note.translation)
    } ?: entry
}
