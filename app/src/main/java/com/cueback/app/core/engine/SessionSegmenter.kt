package com.cueback.app.core.engine

import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.BoundaryReason
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.WorkEvent
import kotlinx.serialization.Serializable

@Serializable
data class OpenSession(
    val startedAt: Long,
    val events: List<WorkEvent> = emptyList(),
    val usage: Map<String, Long> = emptyMap(),
    val trackedPkg: String? = null,
    val trackedSince: Long? = null,
    val attachedContextId: String? = null,
)

@Serializable
data class SegmenterState(
    val session: OpenSession? = null,
    val locked: Boolean = false,
    val awaySince: Long? = null,
    val untrackedAway: Boolean = false,
    val lastEventAt: Long = 0L,
)

data class SessionDraft(
    val startedAt: Long,
    val endedAt: Long,
    val events: List<WorkEvent>,
    val usage: List<AppUsage>,
    val boundary: BoundaryReason,
    val attachedContextId: String?,
    val pauseScore: Double,
)

sealed interface SegmentOutput {
    data class Finalized(val draft: SessionDraft) : SegmentOutput
    /** A tracked app gained focus with no open session: a return candidate. */
    data class TrackedActivity(val packageName: String, val at: Long) : SegmentOutput
    /** Tracked foreground span closed (used for re-entry measurement). */
    data class TrackedSpan(val packageName: String, val start: Long, val end: Long) : SegmentOutput
}

/**
 * Incremental, deterministic session boundary detector. Multiple signals are combined into a pause
 * score; a single weak signal (a glance at another app) never ends a session.
 */
class SessionSegmenter(
    private val config: EngineConfig,
    private val isTracked: (String) -> Boolean,
) {

    fun pauseScore(state: SegmenterState, now: Long): Double {
        val awaySince = state.awaySince ?: return 0.0
        var score = 0.0
        if (state.locked) score += config.weightLocked
        if (state.untrackedAway) score += config.weightUntrackedApp
        if (now - awaySince >= config.idleThresholdMs) score += config.weightIdle
        return score.coerceAtMost(1.0)
    }

    fun tick(state: SegmenterState, now: Long): Pair<SegmenterState, List<SegmentOutput>> {
        val session = state.session ?: return state to emptyList()
        val awaySince = state.awaySince ?: return state to emptyList()
        val score = pauseScore(state, now)
        if (score < config.finalizeThreshold || now - awaySince < config.minAwayMs) return state to emptyList()
        val reason = when {
            state.locked -> BoundaryReason.DEVICE_LOCKED
            state.untrackedAway -> BoundaryReason.LEFT_TRACKED_APP
            else -> BoundaryReason.IDLE
        }
        return finalize(state, session, awaySince, reason, score)
    }

    fun process(state0: SegmenterState, event: WorkEvent): Pair<SegmenterState, List<SegmentOutput>> {
        val out = mutableListOf<SegmentOutput>()
        val (ticked, tickOut) = tick(state0, event.timestamp)
        out += tickOut
        var state = ticked.copy(lastEventAt = maxOf(ticked.lastEventAt, event.timestamp))
        val ts = event.timestamp

        when (event.type) {
            EventType.APP_FOCUSED -> {
                val pkg = event.packageName ?: return state to out
                state = closeSpan(state, ts, out)
                if (isTracked(pkg)) {
                    val session = state.session
                    state = if (session == null) {
                        out += SegmentOutput.TrackedActivity(pkg, ts)
                        state.copy(session = OpenSession(startedAt = ts, events = listOf(event), trackedPkg = pkg, trackedSince = ts))
                    } else {
                        val last = session.events.lastOrNull { it.type == EventType.APP_FOCUSED }
                        val events = if (last?.packageName == pkg) session.events else session.events + event
                        state.copy(session = session.copy(events = events, trackedPkg = pkg, trackedSince = ts))
                    }
                    state = state.copy(awaySince = null, untrackedAway = false, locked = false)
                } else if (state.session != null) {
                    state = state.copy(awaySince = state.awaySince ?: ts, untrackedAway = true, locked = false)
                }
            }

            EventType.DEVICE_LOCKED -> {
                state = closeSpan(state, ts, out)
                state = state.copy(locked = true, awaySince = if (state.session != null) state.awaySince ?: ts else null)
            }

            EventType.DEVICE_UNLOCKED -> state = state.copy(locked = false)

            EventType.EXPLICIT_PAUSE -> {
                state = closeSpan(state, ts, out)
                val session = state.session
                if (session != null) {
                    val withEvent = state.copy(session = session.copy(events = session.events + event))
                    val (s, o) = finalize(withEvent, withEvent.session!!, ts, BoundaryReason.EXPLICIT_PAUSE, config.weightExplicitPause)
                    state = s
                    out += o
                }
            }

            EventType.EXPLICIT_RESUME -> {
                val session = state.session
                state = if (session == null) {
                    state.copy(session = OpenSession(startedAt = ts, events = listOf(event), attachedContextId = event.contextId))
                } else {
                    state.copy(session = session.copy(events = session.events + event, attachedContextId = event.contextId ?: session.attachedContextId))
                }
                if (state.session?.trackedPkg == null && state.awaySince == null) {
                    state = state.copy(awaySince = ts)
                }
            }

            EventType.SHARE_RECEIVED, EventType.MANUAL_NOTE -> {
                val session = state.session
                state = if (session == null) {
                    state.copy(session = OpenSession(startedAt = ts, events = listOf(event)), awaySince = ts, untrackedAway = true)
                } else {
                    state.copy(session = session.copy(events = session.events + event))
                }
            }
        }
        return state to out
    }

    private fun closeSpan(state: SegmenterState, ts: Long, out: MutableList<SegmentOutput>): SegmenterState {
        val session = state.session ?: return state
        val pkg = session.trackedPkg ?: return state
        val since = session.trackedSince ?: return state
        val dur = (ts - since).coerceAtLeast(0)
        out += SegmentOutput.TrackedSpan(pkg, since, ts)
        val usage = session.usage + (pkg to ((session.usage[pkg] ?: 0L) + dur))
        return state.copy(session = session.copy(usage = usage, trackedPkg = null, trackedSince = null))
    }

    private fun finalize(
        state: SegmenterState,
        session0: OpenSession,
        endedAt: Long,
        reason: BoundaryReason,
        score: Double,
    ): Pair<SegmenterState, List<SegmentOutput>> {
        val out = mutableListOf<SegmentOutput>()
        val closed = closeSpan(state.copy(session = session0), endedAt, out)
        val session = closed.session!!
        val reset = closed.copy(session = null, awaySince = null, untrackedAway = false)
        val trackedMs = session.usage.values.sum()
        val hasUserEvidence = session.events.any {
            it.type == EventType.MANUAL_NOTE || it.type == EventType.SHARE_RECEIVED || it.type == EventType.EXPLICIT_PAUSE
        }
        if (trackedMs < config.minSessionMs && !hasUserEvidence && session.attachedContextId == null) {
            return reset to out
        }
        out += SegmentOutput.Finalized(
            SessionDraft(
                startedAt = session.startedAt,
                endedAt = endedAt,
                events = session.events,
                usage = session.usage.map { AppUsage(it.key, it.value) }.sortedByDescending { it.foregroundMs },
                boundary = reason,
                attachedContextId = session.attachedContextId,
                pauseScore = score,
            ),
        )
        return reset to out
    }
}
