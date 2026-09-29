package com.cueback.app.core.engine

import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.DeltaItem
import com.cueback.app.core.model.DeltaKind
import com.cueback.app.core.model.RecoveryLevel
import com.cueback.app.core.model.ReentrySession

enum class DepthPreference { AUTO, CONCISE, FULL }

/** Chrono-adaptive depth: longer, more disruptive, or less certain returns get more reconstruction. */
class DepthSelector(private val config: EngineConfig) {
    fun select(
        awayMs: Long,
        matchScore: Double,
        delta: List<DeltaItem>,
        preference: DepthPreference = DepthPreference.AUTO,
        explicitFull: Boolean = false,
    ): RecoveryLevel {
        if (explicitFull || preference == DepthPreference.FULL) return RecoveryLevel.FULL
        var depth = when {
            awayMs < config.microMaxMs -> 1
            awayMs < config.fullMinMs -> 2
            else -> 3
        }
        if (delta.count { it.changed } >= config.significantDeltaCount) depth += 1
        if (matchScore < config.autoThreshold) depth += 1
        if (preference == DepthPreference.CONCISE) depth -= 1
        return RecoveryLevel.entries.first { it.depth == depth.coerceIn(1, 3) }
    }
}

/** Compares the state saved at pause time against what can be observed now. Only meaningful items. */
class DeltaBuilder(private val labelOf: (String) -> String) {
    fun build(
        ctx: ContextCapsule,
        now: Long,
        awayUsage: List<AppUsage>,
        showAppNames: Boolean,
        currentVersionOf: (String) -> Long?,
        artifactAvailable: (Artifact) -> Boolean?,
    ): List<DeltaItem> {
        val items = mutableListOf<DeltaItem>()
        val pausedAt = ctx.pausedAt ?: return items
        items += DeltaItem(DeltaKind.TIME_AWAY, "Away for ${formatDuration(now - pausedAt)}", changed = false)

        val contextApps = ctx.apps.map { it.packageName }.toSet() + listOfNotNull(ctx.primaryApp)
        val elsewhere = awayUsage.filter { it.packageName !in contextApps && it.foregroundMs >= 30_000 }
            .sortedByDescending { it.foregroundMs }
        if (elsewhere.isNotEmpty()) {
            val total = elsewhere.sumOf { it.foregroundMs }
            val text = if (showAppNames) {
                "Pulled away by " + elsewhere.take(3).joinToString(", ") { "${labelOf(it.packageName)} (${formatDuration(it.foregroundMs)})" }
            } else {
                "${formatDuration(total)} in ${elsewhere.size} other app${if (elsewhere.size == 1) "" else "s"}"
            }
            items += DeltaItem(DeltaKind.INTERRUPTIONS, text, changed = false)
        }

        ctx.primaryApp?.let { pkg ->
            val now = currentVersionOf(pkg)
            val then = ctx.primaryAppVersion
            when {
                now == null && then != null -> items += DeltaItem(DeltaKind.APP_MISSING, "${labelOf(pkg)} is no longer installed", changed = true)
                then != null && now != then -> items += DeltaItem(DeltaKind.APP_UPDATED, "${labelOf(pkg)} was updated while you were away", changed = true)
                then != null -> items += DeltaItem(DeltaKind.APP_UNCHANGED, "${labelOf(pkg)} unchanged", changed = false)
            }
        }

        ctx.artifacts.filter { it.type == ArtifactType.FILE }.forEach { a ->
            when (artifactAvailable(a)) {
                false -> items += DeltaItem(DeltaKind.ARTIFACT_MISSING, "${CapsuleBuilder.describe(a)} can no longer be opened", changed = true)
                true -> items += DeltaItem(DeltaKind.ARTIFACT_OK, "${CapsuleBuilder.describe(a)} still available", changed = false)
                null -> Unit
            }
        }
        return items
    }

    companion object {
        fun formatDuration(ms: Long): String {
            val totalMin = (ms / 60_000).coerceAtLeast(0)
            return when {
                ms < 60_000 -> "${(ms / 1000).coerceAtLeast(1)}s"
                totalMin < 60 -> "$totalMin min"
                totalMin < 48 * 60 -> "${totalMin / 60}h ${totalMin % 60}m".replace(" 0m", "")
                else -> "${totalMin / (60 * 24)} days"
            }
        }
    }
}

/**
 * Re-entry Time = start of the first sustained work span (>= meaningfulDwellMs in an app of this
 * context, or any tracked app for manual contexts) after the warm start was shown.
 */
class ReentryTracker(private val config: EngineConfig) {
    data class Span(val packageName: String, val start: Long, val end: Long)

    fun meaningfulActionAt(session: ReentrySession, spans: List<Span>, contextApps: Set<String>, now: Long): Long? {
        return spans
            .filter { it.start >= session.warmStartAt && (contextApps.isEmpty() || it.packageName in contextApps) }
            .filter { it.end - it.start >= config.meaningfulDwellMs }
            .minByOrNull { it.start }
            ?.start
    }

    /** Ongoing span counts too once it has lasted long enough. */
    fun ongoingMeaningful(session: ReentrySession, pkg: String?, since: Long?, contextApps: Set<String>, now: Long): Long? {
        if (pkg == null || since == null) return null
        if (contextApps.isNotEmpty() && pkg !in contextApps) return null
        val start = maxOf(since, session.warmStartAt)
        return start.takeIf { now - it >= config.meaningfulDwellMs }
    }

    fun expired(session: ReentrySession, now: Long) = now - session.warmStartAt > config.reentryTimeoutMs
}
