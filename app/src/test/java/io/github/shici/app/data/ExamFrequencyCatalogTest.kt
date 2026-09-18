package io.github.shici.app.data

import io.github.shici.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ExamFrequencyCatalogTest {
    private val fixture = """{
      "schemaVersion":1,
      "corpora":[{"id":"test","title":"TEST ONLY","exam":"英语（一）","papers":[
        {"id":"p","year":2020,"sourceUrl":"https://example.org/test"}],
        "methodologyUrl":"https://example.org/method","license":"TEST ONLY"}],
      "words":[{"word":"test","corpusId":"test","reviewed":true,"senses":[
        {"id":"u","text":"未统计测试义项","occurrences":null},
        {"id":"c","text":"已统计测试义项","reviewedBy":"test reviewer","occurrences":[
          {"paperId":"p","location":"reading question 1 token 7","reviewedBy":"test reviewer"}]}]}]
    }"""

    @Test fun `catalog connects evidence to the right word without overwriting its original definitions`() {
        val catalog = ExamFrequencyCatalog(fixture)
        val original = WordEntry("test", "", listOf(Sense("old", "原文")))
        val entry = catalog.apply(original)
        assertEquals(1, catalog.size)
        assertEquals(listOf(1, null), entry.learningSenses().map { it.examCount })
        assertEquals(original.senses, entry.senses)
        assertFalse(catalog.apply(original.copy(word = "other")).hasExamStatistics)
    }

    @Test(expected = IllegalArgumentException::class) fun `unreviewed catalog record is rejected`() {
        ExamFrequencyCatalog(fixture.replace("\"reviewed\":true", "\"reviewed\":false"))
    }

    @Test(expected = IllegalArgumentException::class) fun `English two catalog is rejected`() {
        ExamFrequencyCatalog(fixture.replace("英语（一）", "英语（二）"))
    }
}
