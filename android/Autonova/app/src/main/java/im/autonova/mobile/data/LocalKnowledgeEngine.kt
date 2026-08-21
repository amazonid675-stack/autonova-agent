package im.autonova.mobile.data

/** A bounded, local lexical retrieval index. It never uploads document text and labels its non-vector strategy honestly. */
data class LocalKnowledgeResult(val title: String, val excerpt: String, val score: Int)

class LocalKnowledgeEngine(private val cache: AgentCacheDao) {
    suspend fun index(documentId: String, title: String, text: String): Result<Int> = runCatching {
        val normalized = text.replace("\u0000", " ").replace(Regex("\\s+"), " ").trim()
        require(normalized.length >= 20) { "This document does not contain enough readable text to index locally." }
        require(normalized.length <= 1_000_000) { "Local indexing is limited to 1 MB of readable text per document." }
        val chunks = normalized.chunked(900).mapIndexed { index, content ->
            CachedKnowledgeChunk("$documentId:$index", documentId, title, content, tokenize(content).joinToString(" "), System.currentTimeMillis())
        }
        cache.deleteKnowledgeDocument(documentId); cache.upsertKnowledgeChunks(chunks); chunks.size
    }

    suspend fun retrieve(query: String, limit: Int = 3): List<LocalKnowledgeResult> {
        val queryTerms = tokenize(query).toSet(); if (queryTerms.isEmpty()) return emptyList()
        return cache.localKnowledgeChunks().mapNotNull { chunk ->
            val score = tokenize(chunk.terms).count { it in queryTerms }
            if (score == 0) null else LocalKnowledgeResult(chunk.title, chunk.content.take(850), score)
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun tokenize(value: String): List<String> = value.lowercase().split(Regex("[^a-z0-9_]{1,}")).filter { it.length >= 3 }.distinct()
}
