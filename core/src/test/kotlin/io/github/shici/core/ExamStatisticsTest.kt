package io.github.shici.core

import org.junit.Assert.*
import org.junit.Test

/** Invented fixtures exercise validation; they are never included in the shipped catalog. */
class ExamStatisticsTest {
    private val corpus = ExamCorpus("fixture", "TEST ONLY", "英语（一）",
        listOf(ExamPaper("p", 2020, "https://example.org/test-only")), "https://example.org/method", "TEST ONLY")
    private fun occurrence(location: String) = ExamOccurrence("p", location, "test reviewer")
    private fun sense(id: String, count: Int?) = CountedSense(id, "释义 $id",
        count?.let { (1..it).map { index -> occurrence("$id token $index") } }, if (count != null) "test reviewer" else null)

    @Test fun `counted occurrences sort descending with stable ties then verified zero then unknown`() {
        val stats = ExamStatistics(corpus, listOf(sense("unknown", null), sense("zero", 0), sense("low", 1), sense("high", 3), sense("tie", 1)))
        val entry = WordEntry("fixture", "", listOf(Sense("original", "原词典")), examStatistics = stats)
        assertEquals(listOf("high", "low", "tie", "zero", "unknown"), entry.learningSenses().map { it.id })
        assertEquals(listOf(3, 1, 1, 0, null), entry.learningSenses().map { it.examCount })
        assertEquals("原词典", entry.senses.single().text)
        assertTrue(entry.hasExamStatistics)
    }

    @Test fun `legacy word counts or source labels alone are not sense evidence`() {
        val senses = listOf(Sense("a", "原序 1", 999, "词频"), Sense("b", "原序 2", 1, "某来源"))
        val entry = WordEntry("fixture", "", senses, editorialSenses = listOf(Sense("e", "编辑优先")))
        assertFalse(entry.hasExamStatistics)
        assertEquals(senses, entry.learningSenses())
        assertTrue(entry.senseLabel.contains("未统计"))
    }

    @Test(expected = IllegalArgumentException::class) fun `same token cannot count as two senses`() {
        val evidence = listOf(occurrence("reading question 1 token 7"))
        ExamStatistics(corpus, listOf(CountedSense("a", "甲", evidence, "reviewer"), CountedSense("b", "乙", evidence, "reviewer")))
    }

    @Test(expected = IllegalArgumentException::class) fun `zero requires explicit review`() { CountedSense("a", "甲", emptyList()) }

    @Test(expected = IllegalArgumentException::class) fun `unlisted paper cannot silently expand scope`() {
        ExamStatistics(corpus, listOf(CountedSense("a", "甲", listOf(ExamOccurrence("missing", "token 1", "reviewer")), "reviewer")))
    }

    @Test(expected = IllegalArgumentException::class) fun `English two cannot be combined with English one`() { corpus.copy(exam = "英语（二）") }
}
