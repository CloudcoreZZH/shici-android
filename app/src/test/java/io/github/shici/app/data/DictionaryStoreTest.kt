package io.github.shici.app.data

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class DictionaryStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dictionary = DictionaryStore(context)
    @After fun close() = dictionary.close()

    @Test fun `references retain source case and do not replace base meanings`() {
        val entry = dictionary.find(" HOSPITAL ")!!
        assertEquals("ECDICT", entry.source)
        assertTrue(entry.senses.any { "医院" in it.text })
        assertTrue(entry.references.any { it.headword == "hospital" && it.senses.any { line -> "醫院" in line } })
        assertTrue(entry.references.any { it.headword == "Hospital" })
        assertTrue(entry.references.all { it.url.startsWith("https://zh.wiktionary.org/wiki/") })
        assertTrue(entry.inNetem2024)
        assertFalse(entry.hasExamStatistics)
    }

    @Test fun `empty wiki definitions and historical word counts never become exam senses`() {
        val entry = dictionary.find("address")!!
        assertTrue(entry.references.isEmpty())
        assertTrue(entry.editorialSenses.isNotEmpty())
        assertEquals(entry.editorialSenses, entry.learningSenses())
        assertTrue(entry.inNetem2024)
        assertFalse(entry.hasExamStatistics)
        assertTrue(entry.senses.all { it.examCount == null && it.examSource == null })
    }

    @Test fun `new reference words can be searched and learned with honest source`() {
        val entry = dictionary.find("anti-pollutant")!!
        assertEquals("中文维基词典", entry.source)
        assertTrue(entry.learningSenses().isNotEmpty())
        assertFalse(entry.hasExamStatistics)
        val results = dictionary.search("anti-pollut")
        assertTrue(results.any { it.word == "anti-pollutant" })
        assertEquals(results.map { it.word }.distinct().sorted(), results.map { it.word })
        assertEquals(1, dictionary.search("a", 1).size)
        assertNull(dictionary.find("zzzz-no-such-shici-word"))
    }

    @Test fun `counts match bundled quality audit without touching personal database`() {
        assertEquals(768739, dictionary.baseSize)
        assertEquals(33998, dictionary.referenceSize)
        assertEquals(778580, dictionary.size())
        assertEquals(120, dictionary.editorialSize)
        assertTrue(context.databaseList().isEmpty())
    }
}
