package com.cueback.app.core.model

import kotlinx.serialization.Serializable

/** Where a piece of context came from. The UI must never present [INFERRED] or [UNKNOWN] as fact. */
@Serializable
enum class Provenance { DETECTED, INFERRED, USER, UNKNOWN }

@Serializable
data class Fact(val text: String, val provenance: Provenance) {
    companion object {
        fun user(text: String) = Fact(text, Provenance.USER)
        fun detected(text: String) = Fact(text, Provenance.DETECTED)
        fun inferred(text: String) = Fact(text, Provenance.INFERRED)
    }
}

@Serializable
enum class EventType {
    APP_FOCUSED,
    DEVICE_LOCKED,
    DEVICE_UNLOCKED,
    SHARE_RECEIVED,
    MANUAL_NOTE,
    EXPLICIT_PAUSE,
    EXPLICIT_RESUME,
}

@Serializable
enum class NoteKind { NOTE, DONE, BLOCKER, NEXT, GOAL }

@Serializable
enum class ArtifactType { URL, TEXT, FILE, APP }

@Serializable
data class Artifact(
    val type: ArtifactType,
    val locator: String,
    val title: String? = null,
    val sourcePackage: String? = null,
    val capturedAt: Long = 0L,
)

/**
 * A structured work signal. Events never contain raw screen content; SHARE_RECEIVED carries only what
 * the user explicitly shared into CueBack.
 */
@Serializable
data class WorkEvent(
    val timestamp: Long,
    val type: EventType,
    val packageName: String? = null,
    val artifact: Artifact? = null,
    val noteKind: NoteKind? = null,
    val note: String? = null,
    val contextId: String? = null,
)

@Serializable
data class AppUsage(val packageName: String, val foregroundMs: Long)

@Serializable
enum class ContextStatus { ACTIVE, PAUSED, COMPLETED, ARCHIVED }

@Serializable
enum class UseCase { CODING, STUDY, WRITING, RESEARCH, DESIGN, GENERAL }

@Serializable
enum class BoundaryReason { DEVICE_LOCKED, LEFT_TRACKED_APP, IDLE, EXPLICIT_PAUSE, MANUAL }

@Serializable
data class ContextCapsule(
    val id: String,
    val title: Fact,
    val goal: Fact?,
    val currentState: Fact?,
    val completed: List<Fact>,
    val blocker: Fact?,
    val nextAction: Fact?,
    val notes: List<Fact>,
    val artifacts: List<Artifact>,
    val anchor: Artifact?,
    val primaryApp: String?,
    val apps: List<AppUsage>,
    val keywords: Set<String>,
    val confidence: Double,
    val createdAt: Long,
    val pausedAt: Long?,
    val resumedAt: Long?,
    val status: ContextStatus,
    val boundary: BoundaryReason?,
    val evidence: List<String>,
    val useCase: UseCase = UseCase.GENERAL,
    val isDemo: Boolean = false,
    val muted: Boolean = false,
    val primaryAppVersion: Long? = null,
    /** Reset whenever the context is paused again, so each pause gets at most one of each notification. */
    val lastNotifiedAt: Long? = null,
    val remindedAt: Long? = null,
    val aiRefined: Boolean = false,
) {
    val needsIntent: Boolean get() = nextAction == null || nextAction.provenance == Provenance.UNKNOWN
}

@Serializable
enum class RecoveryLevel(val depth: Int) { MICRO(1), CARD(2), FULL(3) }

@Serializable
enum class MatchBand { AUTO, SUGGEST, INBOX, IGNORE }

@Serializable
enum class DeltaKind { TIME_AWAY, INTERRUPTIONS, APP_UPDATED, APP_UNCHANGED, ARTIFACT_MISSING, ARTIFACT_OK, APP_MISSING }

@Serializable
data class DeltaItem(val kind: DeltaKind, val text: String, val changed: Boolean)

@Serializable
data class WarmStart(
    val contextId: String,
    val level: RecoveryLevel,
    val matchScore: Double,
    val band: MatchBand,
    val awayMs: Long,
    val delta: List<DeltaItem>,
)

@Serializable
data class ReentrySession(
    val id: String,
    val contextId: String,
    val warmStartAt: Long,
    val acceptedAt: Long? = null,
    val firstMeaningfulActionAt: Long? = null,
    val level: RecoveryLevel = RecoveryLevel.CARD,
) {
    val reentryMs: Long? get() = firstMeaningfulActionAt?.let { (it - warmStartAt).coerceAtLeast(0) }
}
