package com.cueback.app.detect

import android.content.Context
import android.net.Uri
import com.cueback.app.billing.BillingRepository
import com.cueback.app.billing.FeatureGate
import com.cueback.app.billing.isPro
import com.cueback.app.core.engine.ContextEngine
import com.cueback.app.core.engine.DepthPreference
import com.cueback.app.core.engine.EngineAction
import com.cueback.app.core.engine.EngineConfig
import com.cueback.app.core.engine.EngineEnvironment
import com.cueback.app.core.engine.MatchResult
import com.cueback.app.core.engine.ReentryTracker
import com.cueback.app.core.engine.SegmenterState
import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.MatchBand
import com.cueback.app.core.model.RecoveryLevel
import com.cueback.app.core.model.UseCase
import com.cueback.app.core.model.WarmStart
import com.cueback.app.core.model.WorkEvent
import com.cueback.app.data.db.CueBackDatabase
import com.cueback.app.data.db.EventEntity
import com.cueback.app.data.repo.AnalyticsRepository
import com.cueback.app.data.repo.ContextRepository
import com.cueback.app.data.repo.Metric
import com.cueback.app.data.repo.ReentryRecord
import com.cueback.app.data.repo.ReentryRepository
import com.cueback.app.data.repo.SettingsRepository
import com.cueback.app.data.repo.TimelineKind
import com.cueback.app.data.repo.WarmStartSource
import com.cueback.app.notify.NotificationKind
import com.cueback.app.notify.NotificationPolicy
import com.cueback.app.notify.Notifier
import com.cueback.app.notify.PushService
import com.cueback.app.platform.AppCatalogApi
import com.cueback.app.platform.UsageSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

data class Suggestion(val contextId: String, val warmStart: WarmStart, val match: MatchResult, val at: Long)

data class WarmStartView(
    val context: ContextCapsule,
    val warmStart: WarmStart,
    /** True when the engine chose FULL but the free tier shows the recovery card. */
    val fullLocked: Boolean,
)

enum class DetectionHealth { OFF_NO_APPS, OFF_NO_PERMISSION, PAUSED, ACTIVE }

/**
 * The single place where platform signals meet the engine. Every trigger — the periodic worker, the
 * live service, the share sheet, in-app actions, the demo fixture — goes through [runPass].
 */
class DetectionCoordinator(
    private val appContext: Context,
    private val db: CueBackDatabase,
    private val settings: SettingsRepository,
    private val contexts: ContextRepository,
    private val reentry: ReentryRepository,
    private val usage: UsageSource,
    private val catalog: AppCatalogApi,
    private val notifier: Notifier,
    private val push: PushService,
    private val billing: BillingRepository,
    private val analytics: AnalyticsRepository,
    private val clock: () -> Long,
    private val newId: () -> String,
    val config: EngineConfig = EngineConfig(),
) {
    private val mutex = Mutex()
    private val tracker = ReentryTracker(config)

    private val _suggestion = MutableStateFlow<Suggestion?>(null)
    val suggestion: StateFlow<Suggestion?> = _suggestion.asStateFlow()

    private val _completedReentry = MutableStateFlow<ReentryRecord?>(null)
    val completedReentry: StateFlow<ReentryRecord?> = _completedReentry.asStateFlow()

    private val _archivedForLimit = MutableStateFlow(false)
    val archivedForLimit: StateFlow<Boolean> = _archivedForLimit.asStateFlow()

    fun consumeCompletedReentry() { _completedReentry.value = null }
    fun dismissLimitNotice() { _archivedForLimit.value = false }
    fun clearSuggestion(contextId: String? = null) {
        if (contextId == null || _suggestion.value?.contextId == contextId) _suggestion.value = null
    }

    suspend fun health(): DetectionHealth {
        val s = settings.current()
        return when {
            s.trackedApps.isEmpty() -> DetectionHealth.OFF_NO_APPS
            !usage.hasPermission() -> DetectionHealth.OFF_NO_PERMISSION
            s.collectionPaused -> DetectionHealth.PAUSED
            else -> DetectionHealth.ACTIVE
        }
    }

    suspend fun submit(event: WorkEvent) = runPass(listOf(event))

    suspend fun runPass(extra: List<WorkEvent> = emptyList()) = mutex.withLock {
        val now = clock()
        val s = settings.current()
        val gate = FeatureGate(billing.state.value)
        val tracked = gate.effectiveTracked(s.trackedApps)
        val collecting = !s.collectionPaused && tracked.isNotEmpty() && usage.hasPermission()

        val cursor = settings.usageCursor()
        val from = maxOf((cursor ?: (now - FIRST_LOOKBACK_MS)) + 1, now - MAX_LOOKBACK_MS)
        val observed = if (collecting) usage.events(from, now, tracked::contains) else emptyList()
        val events = (observed + extra.filter { it.timestamp <= now }).sortedBy { it.timestamp }

        val engine = ContextEngine(config, catalog::labelOf, tracked::contains, newId)
        val env = environment(s.useCase, s.showInterruptionAppNames, s.depthPreference, contexts.open(), demo = false)
        val state0 = settings.segmenterState()
        val (state, actions) = engine.ingest(state0, events, now, env)
        settings.saveSegmenterState(state)
        settings.setUsageCursor(now)

        if (observed.isNotEmpty()) {
            db.events().insertAll(observed.map { EventEntity(timestamp = it.timestamp, type = it.type.name, packageName = it.packageName, json = "{}") })
            db.events().prune(now - EVENT_RETENTION_MS)
        }
        handle(actions, s, gate, now)
        checkReentry(actions.filterIsInstance<EngineAction.Span>().map { it.span }, state, now)
        syncTags(s.useCase, gate.pro)
    }

    /** Stop observing immediately: close the open session and forget the in-flight state. */
    suspend fun pauseCollection() {
        runPass(listOf(WorkEvent(clock(), EventType.EXPLICIT_PAUSE)))
        mutex.withLock { settings.saveSegmenterState(SegmenterState()) }
    }

    suspend fun openSessionActive(): Boolean = settings.segmenterState().session != null

    suspend fun warmStartView(contextId: String, explicitFull: Boolean = false): WarmStartView? {
        val ctx = contexts.get(contextId) ?: return null
        val s = settings.current()
        val gate = FeatureGate(billing.state.value)
        val now = clock()
        val match = _suggestion.value?.takeIf { it.contextId == contextId }?.match
            ?: MatchResult(contextId, 1.0, MatchBand.AUTO, listOf("You chose this context"))
        val engine = ContextEngine(config, catalog::labelOf, { false }, newId)
        val env = environment(s.useCase, s.showInterruptionAppNames, s.depthPreference, listOf(ctx), demo = ctx.isDemo)
        val ws = engine.warmStart(ctx, now, match, env, explicitFull)
        val capped = gate.cap(ws.level)
        return WarmStartView(ctx, ws.copy(level = capped), fullLocked = capped != ws.level)
    }

    suspend fun onWarmStartShown(contextId: String, level: RecoveryLevel, source: WarmStartSource): String {
        val session = reentry.start(contextId, clock(), level, source)
        contexts.update(contextId, TimelineKind.WARM_START, "Warm start shown (${level.name.lowercase()})") { it }
        analytics.track(Metric.WARM_START_SHOWN, level.depth.toDouble())
        if (source == WarmStartSource.NOTIFICATION) analytics.track(Metric.NOTIFICATION_OPENED)
        return session.id
    }

    suspend fun onWarmStartAccepted(sessionId: String, contextId: String) {
        val now = clock()
        reentry.accept(sessionId, now)
        contexts.update(contextId, TimelineKind.RESUMED, "Resumed from warm start") { it.copy(status = ContextStatus.ACTIVE, resumedAt = now) }
        notifier.cancelFor(contextId)
        clearSuggestion(contextId)
        analytics.track(Metric.WARM_START_ACCEPTED)
        push.outcome("warm_start_accepted")
        submit(WorkEvent(now, EventType.EXPLICIT_RESUME, contextId = contextId))
    }

    suspend fun onWarmStartRejected(sessionId: String?, contextId: String) {
        sessionId?.let { reentry.reject(it) }
        clearSuggestion(contextId)
        analytics.track(Metric.WARM_START_REJECTED)
    }

    /** User says they are back on track: records a meaningful action with user provenance. */
    suspend fun markBackOnTrack(sessionId: String) {
        val rec = reentry.complete(sessionId, clock()) ?: return
        onReentryCompleted(rec)
    }

    suspend fun addNote(contextId: String?, kind: com.cueback.app.core.model.NoteKind, text: String) {
        if (contextId != null && !openSessionActive()) {
            contexts.update(contextId, TimelineKind.EDITED, "Note added") { c ->
                val fact = com.cueback.app.core.model.Fact.user(text.trim())
                when (kind) {
                    com.cueback.app.core.model.NoteKind.NEXT -> c.copy(nextAction = fact)
                    com.cueback.app.core.model.NoteKind.BLOCKER -> c.copy(blocker = fact)
                    com.cueback.app.core.model.NoteKind.GOAL -> c.copy(goal = fact)
                    com.cueback.app.core.model.NoteKind.DONE -> c.copy(completed = c.completed + fact)
                    com.cueback.app.core.model.NoteKind.NOTE -> c.copy(notes = c.notes + fact)
                }
            }
            return
        }
        submit(WorkEvent(clock(), EventType.MANUAL_NOTE, noteKind = kind, note = text.trim(), contextId = contextId))
    }

    /** Share-sheet capture: attach to a chosen context directly, or to the live session. */
    suspend fun addShared(artifact: Artifact, contextId: String?) {
        if (contextId != null) {
            contexts.update(contextId, TimelineKind.EDITED, "Saved ${artifact.type.name.lowercase()}") { c ->
                c.copy(artifacts = (c.artifacts + artifact).distinctBy { it.locator }, anchor = artifact)
            }
        } else {
            submit(WorkEvent(artifact.capturedAt, EventType.SHARE_RECEIVED, artifact = artifact))
        }
    }

    suspend fun replayDemo(): String? = mutex.withLock {
        contexts.deleteDemo()
        val now = clock()
        val fixture = DemoFixture.jwtRefresh(now)
        val engine = ContextEngine(config, catalog::labelOf, fixture.tracked::contains, newId)
        val s = settings.current()
        var env = environment(UseCase.CODING, true, s.depthPreference, emptyList(), demo = true, fixtureAway = fixture.awayUsage)
        val (state, actions) = engine.ingest(SegmenterState(), fixture.workEvents, fixture.pausedCheckAt, env)
        val capsule = actions.filterIsInstance<EngineAction.SaveContext>().singleOrNull()?.capsule ?: return@withLock null
        contexts.save(capsule, TimelineKind.CREATED, "Captured automatically (demo replay)")
        env = environment(UseCase.CODING, true, s.depthPreference, listOf(capsule), demo = true, fixtureAway = fixture.awayUsage)
        val (_, back) = engine.ingest(state, listOf(fixture.returnEvent), now, env)
        back.filterIsInstance<EngineAction.OfferWarmStart>().firstOrNull()?.let {
            _suggestion.value = Suggestion(it.warmStart.contextId, it.warmStart, it.match, now)
        }
        capsule.id
    }

    suspend fun saveManual(capsule: ContextCapsule) {
        val gate = FeatureGate(billing.state.value)
        if (!gate.canCreateContext(contexts.openCount())) archiveOldest()
        contexts.save(capsule, TimelineKind.CREATED, "Saved manually")
        analytics.track(Metric.CONTEXT_CREATED)
        syncTags(settings.current().useCase, gate.pro)
    }

    suspend fun sendReminders() = mutex.withLock {
        val s = settings.current()
        val gate = FeatureGate(billing.state.value)
        val now = clock()
        for (ctx in contexts.open()) {
            if (ctx.isDemo) continue
            if (NotificationPolicy.allowed(NotificationKind.UNRESOLVED_REMINDER, s, ctx, hourOf(now), now, ctx.remindedAt, gate.pro)) {
                notifier.reminder(ctx, s.lockScreenPrivate)
                contexts.update(ctx.id, TimelineKind.EDITED, "Reminder sent") { it.copy(remindedAt = now) }
            }
        }
    }

    private suspend fun handle(actions: List<EngineAction>, s: com.cueback.app.data.repo.AppSettings, gate: FeatureGate, now: Long) {
        for (a in actions) when (a) {
            is EngineAction.SaveContext -> {
                if (a.isNew && !gate.canCreateContext(contexts.openCount())) archiveOldest()
                contexts.save(
                    a.capsule,
                    if (a.isNew) TimelineKind.CREATED else TimelineKind.EVOLVED,
                    if (a.isNew) "Captured automatically — ${a.capsule.boundary?.name?.lowercase()?.replace('_', ' ')}" else "Context updated after another session",
                )
                analytics.track(if (a.isNew) Metric.AUTO_CONTEXT_CREATED else Metric.CONTEXT_CREATED)
                if (a.askIntent && NotificationPolicy.allowed(NotificationKind.INTENT_PROMPT, s, a.capsule, hourOf(now), now, null, gate.pro)) {
                    notifier.intentPrompt(a.capsule, s.lockScreenPrivate)
                }
            }
            is EngineAction.OfferWarmStart -> {
                val ctx = contexts.get(a.warmStart.contextId) ?: continue
                _suggestion.value = Suggestion(ctx.id, a.warmStart, a.match, now)
                analytics.track(Metric.WARM_START_OFFERED, a.match.score)
                if (NotificationPolicy.allowed(NotificationKind.RESUME_CANDIDATE, s, ctx, hourOf(now), now, ctx.lastNotifiedAt, gate.pro)) {
                    notifier.resumeCandidate(ctx, ctx.primaryApp?.let(catalog::labelOf), s.lockScreenPrivate)
                    contexts.update(ctx.id, TimelineKind.EDITED, "Resume suggestion sent") { it.copy(lastNotifiedAt = now) }
                }
                if (a.match.band == MatchBand.AUTO) {
                    contexts.update(ctx.id, TimelineKind.RESUMED, "Return detected") { it.copy(status = ContextStatus.ACTIVE) }
                }
            }
            is EngineAction.InboxCandidate -> if (_suggestion.value == null) {
                val ctx = contexts.get(a.contextId) ?: continue
                val env = environment(s.useCase, s.showInterruptionAppNames, s.depthPreference, listOf(ctx), demo = ctx.isDemo)
                val match = MatchResult(ctx.id, a.score, MatchBand.INBOX, emptyList())
                val ws = ContextEngine(config, catalog::labelOf, { false }, newId).warmStart(ctx, now, match, env)
                _suggestion.value = Suggestion(ctx.id, ws, match, now)
            }
            is EngineAction.SilentReattach -> contexts.update(a.contextId, TimelineKind.RESUMED, "Picked back up") { it.copy(status = ContextStatus.ACTIVE) }
            is EngineAction.Span -> Unit
        }
    }

    private suspend fun checkReentry(spans: List<ReentryTracker.Span>, state: SegmenterState, now: Long) {
        for (rec in reentry.pending()) {
            val session = rec.session
            if (tracker.expired(session, now)) { reentry.expire(session.id); continue }
            val ctx = contexts.get(session.contextId) ?: continue
            val apps = (ctx.apps.map { it.packageName } + listOfNotNull(ctx.primaryApp)).filter { !it.startsWith("~") }.toSet()
            val at = tracker.meaningfulActionAt(session, spans, apps, now)
                ?: tracker.ongoingMeaningful(session, state.session?.trackedPkg, state.session?.trackedSince, apps, now)
                ?: continue
            reentry.complete(session.id, at)?.let { onReentryCompleted(it) }
        }
    }

    private suspend fun onReentryCompleted(rec: ReentryRecord) {
        val secs = (rec.session.reentryMs ?: return) / 1000.0
        analytics.track(Metric.MEANINGFUL_ACTION_DETECTED)
        analytics.track(Metric.REENTRY_COMPLETED, secs)
        push.outcome("reentry_completed", secs.toFloat())
        _completedReentry.value = rec
    }

    private suspend fun archiveOldest() {
        val oldest = contexts.open().filter { !it.isDemo }.minByOrNull { it.pausedAt ?: it.createdAt } ?: return
        contexts.update(oldest.id, TimelineKind.EDITED, "Archived: free plan keeps 3 open contexts") { it.copy(status = ContextStatus.ARCHIVED) }
        _archivedForLimit.value = true
    }

    private suspend fun syncTags(useCase: UseCase, pro: Boolean) {
        if (!push.configured) return
        val open = contexts.open().filter { !it.isDemo }
        push.syncTags(
            openContexts = open.size,
            unresolvedWithNext = open.count { it.nextAction != null },
            lastPauseAtSec = open.mapNotNull { it.pausedAt }.maxOrNull()?.div(1000),
            useCase = useCase.name,
            pro = pro,
        )
    }

    private fun environment(
        useCase: UseCase,
        showNames: Boolean,
        depth: DepthPreference,
        open: List<ContextCapsule>,
        demo: Boolean,
        fixtureAway: List<AppUsage>? = null,
    ) = object : EngineEnvironment {
        override fun resumableContexts() = open
        override fun appVersion(packageName: String) = catalog.versionOf(packageName)
        override fun artifactAvailable(artifact: Artifact): Boolean? = when (artifact.type) {
            ArtifactType.FILE -> runCatching {
                appContext.contentResolver.openAssetFileDescriptor(Uri.parse(artifact.locator), "r")?.use { true } ?: false
            }.getOrDefault(false)
            else -> null
        }
        override fun usageBetween(from: Long, to: Long) = fixtureAway ?: usage.usageBetween(from, to)
        override val useCase = useCase
        override val showInterruptionAppNames = showNames
        override val depthPreference = depth
        override val demoMode = demo
    }

    private fun hourOf(t: Long) = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).hour

    companion object {
        const val FIRST_LOOKBACK_MS = 15 * 60_000L
        const val MAX_LOOKBACK_MS = 24 * 60 * 60_000L
        const val EVENT_RETENTION_MS = 7 * 24 * 60 * 60_000L
    }
}
