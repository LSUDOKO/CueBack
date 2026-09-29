package com.cueback.app.core.engine

import java.net.URI

object Keywords {
    private val STOP = setOf(
        "the", "and", "for", "with", "that", "this", "from", "into", "your", "you", "are", "was", "were", "have",
        "has", "had", "not", "but", "all", "can", "will", "just", "then", "than", "out", "about", "what", "when",
        "where", "which", "who", "how", "why", "our", "its", "http", "https", "www", "com", "org", "net", "html",
        "work", "next", "run", "use", "get", "new", "one", "two", "via", "docs",
    )
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

    fun extract(texts: List<String?>): Set<String> =
        texts.filterNotNull()
            .flatMap { it.lowercase().split(SPLIT) }
            .filter { it.length >= 3 && it !in STOP && !it.all(Char::isDigit) }
            .toSet()

    fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        return a.intersect(b).size.toDouble() / a.union(b).size
    }

    fun domainOf(locator: String): String? = runCatching {
        val host = URI(locator.trim()).host ?: return null
        host.removePrefix("www.").lowercase()
    }.getOrNull()
}
