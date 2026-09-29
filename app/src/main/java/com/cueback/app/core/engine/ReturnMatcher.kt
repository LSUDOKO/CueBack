package com.cueback.app.core.engine

import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.core.model.MatchBand
import kotlin.math.pow

/** What CueBack observes at the moment of a possible return. Absent signals are not penalized. */
data class ReturnObservation(
    val at: Long,
    val packageName: String? = null,
    val artifact: Artifact? = null,
    val keywords: Set<String> = emptySet(),
    val explicitContextId: String? = null,
)

data class MatchResult(val contextId: String, val score: Double, val band: MatchBand, val reasons: List<String>)

class ReturnMatcher(private val config: EngineConfig) {

    fun band(score: Double): MatchBand = when {
        score >= config.autoThreshold -> MatchBand.AUTO
        score >= config.suggestThreshold -> MatchBand.SUGGEST
        score >= config.inboxThreshold -> MatchBand.INBOX
        else -> MatchBand.IGNORE
    }

    fun score(obs: ReturnObservation, ctx: ContextCapsule): MatchResult {
        if (obs.explicitContextId == ctx.id) return MatchResult(ctx.id, 1.0, MatchBand.AUTO, listOf("You chose this context"))
        var max = 0.0
        var got = 0.0
        val reasons = mutableListOf<String>()

        obs.packageName?.let { pkg ->
            max += config.mPrimaryApp
            when {
                pkg == ctx.primaryApp -> { got += config.mPrimaryApp; reasons += "Same app" }
                ctx.apps.any { it.packageName == pkg } -> { got += config.mSecondaryApp; reasons += "App used in this context" }
            }
        }
        obs.artifact?.let { a ->
            max += config.mArtifactExact + config.mArtifactDomain
            val exact = ctx.artifacts.any { it.locator == a.locator }
            val domain = a.type == ArtifactType.URL && Keywords.domainOf(a.locator)?.let { d ->
                ctx.artifacts.any { Keywords.domainOf(it.locator) == d }
            } == true
            if (exact) { got += config.mArtifactExact + config.mArtifactDomain; reasons += "Same item" }
            else if (domain) { got += config.mArtifactDomain; reasons += "Same site" }
        }
        if (obs.keywords.isNotEmpty() && ctx.keywords.isNotEmpty()) {
            max += config.mKeyword
            val j = Keywords.jaccard(obs.keywords, ctx.keywords)
            if (j > 0) { got += config.mKeyword * (j * 2).coerceAtMost(1.0); reasons += "Similar topic" }
        }
        val pausedAt = ctx.pausedAt
        if (pausedAt != null) {
            max += config.mRecency
            got += config.mRecency * recency(obs.at - pausedAt)
        }
        val s = if (max == 0.0) 0.0 else (got / max).coerceIn(0.0, 1.0)
        return MatchResult(ctx.id, s, band(s), reasons)
    }

    /** Exponential decay; 1.0 at pause time, 0.5 after one half-life. */
    fun recency(elapsedMs: Long): Double = 0.5.pow(elapsedMs.coerceAtLeast(0).toDouble() / config.recencyHalfLifeMs)

    /**
     * Best candidate among resumable contexts. When two contexts are nearly tied, the best score is
     * penalized: an ambiguous match must never auto-open a warm start.
     */
    fun best(obs: ReturnObservation, contexts: List<ContextCapsule>): MatchResult? {
        val scored = contexts
            .filter { it.status == ContextStatus.PAUSED && !it.muted }
            .map { score(obs, it) }
            .sortedByDescending { it.score }
        val top = scored.firstOrNull() ?: return null
        val second = scored.getOrNull(1)
        val explicit = obs.explicitContextId == top.contextId
        val adjusted = if (!explicit && second != null && top.score - second.score < config.ambiguityMargin) {
            top.score * (1 - config.ambiguityPenalty)
        } else top.score
        val result = top.copy(score = adjusted, band = band(adjusted))
        return result.takeIf { it.band != MatchBand.IGNORE }
    }
}
