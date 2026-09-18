package io.github.shici.core

/** Counts are derived from reviewed occurrences, never from word frequency or an editor's order. */
data class ExamPaper(val id: String, val year: Int, val sourceUrl: String) {
    init {
        require(id.isNotBlank() && year >= 2010)
        require(sourceUrl.startsWith("https://"))
    }
}

data class ExamCorpus(val id: String, val title: String, val exam: String, val papers: List<ExamPaper>,
                      val methodologyUrl: String, val license: String) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && exam == "英语（一）")
        require(papers.isNotEmpty() && papers.map { it.id }.distinct().size == papers.size)
        require(methodologyUrl.startsWith("https://") && license.isNotBlank())
    }
    val scope: String get() = "$exam · ${papers.minOf { it.year }}—${papers.maxOf { it.year }} · ${papers.size} 份试卷"
}

/** location includes the section, question and token offset, so two senses cannot count the same token. */
data class ExamOccurrence(val paperId: String, val location: String, val reviewedBy: String) {
    init { require(paperId.isNotBlank() && location.isNotBlank() && reviewedBy.isNotBlank()) }
}

/** null means uncounted; an empty reviewed list means verified zero within this corpus. */
data class CountedSense(val id: String, val text: String, val occurrences: List<ExamOccurrence>?, val reviewedBy: String? = null) {
    init {
        require(id.isNotBlank() && text.isNotBlank())
        require(occurrences == null || !reviewedBy.isNullOrBlank())
    }
    val count: Int? get() = occurrences?.size
}

data class ExamStatistics(val corpus: ExamCorpus, val senses: List<CountedSense>) {
    init {
        require(senses.isNotEmpty() && senses.map { it.id }.distinct().size == senses.size)
        val evidence = senses.flatMap { it.occurrences.orEmpty() }
        require(evidence.all { occurrence -> corpus.papers.any { it.id == occurrence.paperId } })
        require(evidence.map { it.paperId to it.location }.distinct().size == evidence.size)
        require(senses.any { it.count != null })
    }
    // Kotlin's stable sort keeps the catalog order for ties and puts unknowns after verified zeros.
    val ranked: List<CountedSense> get() = senses.sortedWith(compareByDescending { it.count })
}
