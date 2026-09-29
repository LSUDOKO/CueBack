package com.cueback.app.core.engine

import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.Fact
import com.cueback.app.core.model.NoteKind
import com.cueback.app.core.model.Provenance
import com.cueback.app.core.model.UseCase

/**
 * Deterministic Tier-0 reconstruction: turns a finalized session into a Context Capsule. Every field
 * carries provenance, and the next action is never invented without evidence.
 */
class CapsuleBuilder(
    private val config: EngineConfig,
    private val labelOf: (String) -> String,
) {

    fun build(
        id: String,
        draft: SessionDraft,
        existing: ContextCapsule? = null,
        useCase: UseCase = UseCase.GENERAL,
        primaryAppVersion: Long? = null,
        isDemo: Boolean = false,
    ): ContextCapsule {
        val notes = draft.events.filter { it.type == EventType.MANUAL_NOTE && !it.note.isNullOrBlank() }
        fun lastNote(kind: NoteKind) = notes.lastOrNull { it.noteKind == kind }?.note?.trim()

        val shared = draft.events.filter { it.type == EventType.SHARE_RECEIVED }.mapNotNull { it.artifact }
        val usage = mergeUsage(existing?.apps.orEmpty(), draft.usage)
        val sessionPrimary = draft.usage.firstOrNull()?.packageName
        val primaryApp = sessionPrimary ?: existing?.primaryApp
        val lastTrackedPkg = draft.events.lastOrNull { it.type == EventType.APP_FOCUSED }?.packageName ?: primaryApp

        val artifacts = mergeArtifacts(existing?.artifacts.orEmpty(), shared)
        val anchor: Artifact? = shared.lastOrNull()
            ?: lastTrackedPkg?.let { Artifact(ArtifactType.APP, it, labelOf(it), it, draft.endedAt) }
            ?: existing?.anchor

        val goal = lastNote(NoteKind.GOAL)?.let(Fact::user) ?: existing?.goal
        val blocker = lastNote(NoteKind.BLOCKER)?.let(Fact::user) ?: existing?.blocker
        val completed = (existing?.completed.orEmpty() + notes.filter { it.noteKind == NoteKind.DONE }.map { Fact.user(it.note!!.trim()) })
            .distinctBy { it.text.lowercase() }
        val freeNotes = (existing?.notes.orEmpty() + notes.filter { it.noteKind == NoteKind.NOTE }.map { Fact.user(it.note!!.trim()) })
            .distinctBy { it.text.lowercase() }

        val nextAction = lastNote(NoteKind.NEXT)?.let(Fact::user)
            ?: existing?.nextAction?.takeIf { it.provenance == Provenance.USER }
            ?: inferNextFromAnchor(anchor)

        val title = existing?.title?.takeIf { it.provenance == Provenance.USER }
            ?: goal?.let { Fact.inferred(it.text.take(MAX_TITLE)) }
            ?: shared.lastOrNull { !it.title.isNullOrBlank() }?.title?.let { Fact.inferred(it.trim().take(MAX_TITLE)) }
            ?: existing?.title
            ?: notes.firstOrNull()?.note?.let { Fact.inferred(it.trim().take(MAX_TITLE)) }
            ?: primaryApp?.let { Fact.detected("Work in ${labelOf(it)}") }
            ?: Fact(UNTITLED, Provenance.UNKNOWN)

        val currentState = describeState(draft.usage, lastTrackedPkg, shared.lastOrNull())
            ?: existing?.currentState

        val keywords = Keywords.extract(
            listOfNotNull(title.text, goal?.text, blocker?.text, nextAction?.text) +
                freeNotes.map { it.text } + artifacts.mapNotNull { it.title } + artifacts.mapNotNull { Keywords.domainOf(it.locator) },
        )

        val evidence = buildList {
            draft.usage.forEach { add("Detected: ${minutes(it.foregroundMs)} in ${labelOf(it.packageName)}") }
            shared.forEach { add("Detected: shared ${describe(it)}${it.sourcePackage?.let { p -> " from ${labelOf(p)}" } ?: ""}") }
            notes.forEach { add("You said (${it.noteKind!!.name.lowercase()}): ${it.note!!.trim()}") }
            add("Detected: session ended — ${draft.boundary.name.lowercase().replace('_', ' ')}")
        }

        val confidence = confidence(draft, shared, notes.map { it.noteKind }, nextAction, keywordsBySource(title, shared, notes.mapNotNull { it.note }))

        return ContextCapsule(
            id = id,
            title = title,
            goal = goal,
            currentState = currentState,
            completed = completed,
            blocker = blocker,
            nextAction = nextAction,
            notes = freeNotes,
            artifacts = artifacts,
            anchor = anchor,
            primaryApp = primaryApp,
            apps = usage,
            keywords = keywords,
            confidence = confidence,
            createdAt = existing?.createdAt ?: draft.startedAt,
            pausedAt = draft.endedAt,
            resumedAt = existing?.resumedAt,
            status = ContextStatus.PAUSED,
            boundary = draft.boundary,
            evidence = (existing?.evidence.orEmpty() + evidence).takeLast(MAX_EVIDENCE),
            useCase = existing?.useCase ?: useCase,
            isDemo = existing?.isDemo ?: isDemo,
            muted = existing?.muted ?: false,
            primaryAppVersion = primaryAppVersion ?: existing?.primaryAppVersion,
        )
    }

    /** A manual context created from the capture screen: everything is user-supplied. */
    fun manual(
        id: String,
        now: Long,
        title: String,
        goal: String?,
        done: String?,
        blocker: String?,
        next: String?,
        link: Artifact?,
        useCase: UseCase,
    ): ContextCapsule {
        val t = title.trim().ifBlank { next?.trim()?.take(MAX_TITLE) ?: UNTITLED }
        val nextFact = next?.trim()?.takeIf { it.isNotEmpty() }?.let(Fact::user)
        val artifacts = listOfNotNull(link)
        return ContextCapsule(
            id = id,
            title = Fact.user(t),
            goal = goal?.trim()?.takeIf { it.isNotEmpty() }?.let(Fact::user),
            currentState = null,
            completed = listOfNotNull(done?.trim()?.takeIf { it.isNotEmpty() }?.let(Fact::user)),
            blocker = blocker?.trim()?.takeIf { it.isNotEmpty() }?.let(Fact::user),
            nextAction = nextFact,
            notes = emptyList(),
            artifacts = artifacts,
            anchor = link,
            primaryApp = link?.sourcePackage,
            apps = emptyList(),
            keywords = Keywords.extract(listOfNotNull(t, goal, blocker, next, link?.title, link?.let { Keywords.domainOf(it.locator) })),
            confidence = if (nextFact != null) 1.0 else 0.7,
            createdAt = now,
            pausedAt = now,
            resumedAt = null,
            status = ContextStatus.PAUSED,
            boundary = com.cueback.app.core.model.BoundaryReason.MANUAL,
            evidence = listOf("You said: saved manually"),
            useCase = useCase,
        )
    }

    private fun inferNextFromAnchor(anchor: Artifact?): Fact? = when (anchor?.type) {
        ArtifactType.URL, ArtifactType.FILE -> Fact.inferred("Reopen ${describe(anchor)}${anchor.sourcePackage?.let { " in ${labelOf(it)}" } ?: ""}")
        else -> null
    }

    private fun describeState(usage: List<AppUsage>, lastPkg: String?, lastShared: Artifact?): Fact? {
        if (usage.isEmpty() && lastShared == null) return null
        val parts = mutableListOf<String>()
        if (usage.isNotEmpty()) {
            parts += usage.take(3).joinToString(", ") { "${minutes(it.foregroundMs)} in ${labelOf(it.packageName)}" }
        }
        lastPkg?.let { parts += "last in ${labelOf(it)}" }
        lastShared?.let { parts += "last saved ${describe(it)}" }
        return Fact.detected(parts.joinToString(" · "))
    }

    private fun confidence(
        draft: SessionDraft,
        shared: List<Artifact>,
        noteKinds: List<NoteKind?>,
        next: Fact?,
        semanticSources: Int,
    ): Double {
        val total = draft.usage.sumOf { it.foregroundMs }
        val hasUser = noteKinds.isNotEmpty() || shared.isNotEmpty()
        val source = if (total > 0) draft.usage.first().foregroundMs.toDouble() / total else if (hasUser) 0.5 else 0.0
        val artifact = when {
            shared.isNotEmpty() -> 1.0
            draft.usage.isNotEmpty() -> 0.5
            else -> 0.0
        }
        val temporal = (total.toDouble() / config.temporalFullMs).coerceIn(0.0, 1.0)
        val actions = draft.events.count { it.type != EventType.EXPLICIT_PAUSE }
        val sequence = (actions / 4.0).coerceIn(0.0, 1.0)
        val intent = when {
            next?.provenance == Provenance.USER -> 1.0
            noteKinds.isNotEmpty() -> 0.5
            else -> 0.0
        }
        val semantic = when {
            semanticSources >= 2 -> 1.0
            semanticSources == 1 -> 0.5
            else -> 0.0
        }
        val c = config.cSourceContinuity * source +
            config.cArtifactContinuity * artifact +
            config.cTemporalContinuity * temporal +
            config.cActionSequence * sequence +
            config.cExplicitIntent * intent +
            config.cSemantic * semantic
        return c.coerceIn(0.0, 1.0)
    }

    /** Number of independent sources (title, shared items, notes) that agree on at least one keyword. */
    private fun keywordsBySource(title: Fact, shared: List<Artifact>, notes: List<String>): Int {
        val sets = listOf(
            Keywords.extract(listOf(title.text)),
            Keywords.extract(shared.mapNotNull { it.title }),
            Keywords.extract(notes),
        ).filter { it.isNotEmpty() }
        if (sets.size < 2) return sets.size
        val agreeing = sets.indices.count { i -> sets.indices.any { j -> j != i && sets[i].intersect(sets[j]).isNotEmpty() } }
        return if (agreeing >= 2) 2 else 1
    }

    private fun mergeUsage(old: List<AppUsage>, new: List<AppUsage>): List<AppUsage> =
        (old + new).groupBy { it.packageName }
            .map { (pkg, list) -> AppUsage(pkg, list.sumOf { it.foregroundMs }) }
            .sortedByDescending { it.foregroundMs }

    private fun mergeArtifacts(old: List<Artifact>, new: List<Artifact>): List<Artifact> =
        (old + new).reversed().distinctBy { it.type to it.locator }.reversed().takeLast(MAX_ARTIFACTS)

    companion object {
        const val MAX_TITLE = 60
        const val MAX_ARTIFACTS = 30
        const val MAX_EVIDENCE = 40
        const val UNTITLED = "Untitled context"

        fun minutes(ms: Long): String {
            val m = ms / 60_000
            return if (m < 1) "<1 min" else "$m min"
        }

        fun describe(a: Artifact): String = when (a.type) {
            ArtifactType.URL -> a.title?.let { "“${it.take(48)}”" } ?: (Keywords.domainOf(a.locator) ?: "a link")
            ArtifactType.FILE -> a.title?.let { "“${it.take(48)}”" } ?: "a file"
            ArtifactType.TEXT -> "a note"
            ArtifactType.APP -> a.title ?: a.locator
        }
    }
}
