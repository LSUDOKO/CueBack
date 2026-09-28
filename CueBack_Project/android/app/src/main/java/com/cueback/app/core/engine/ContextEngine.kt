package com.cueback.app.core.engine

import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.core.model.MatchBand
import com.cueback.app.core.model.UseCase
import com.cueback.app.core.model.WarmStart
import com.cueback.app.core.model.WorkEvent

/** Side-effect-free view of the world the engine needs. Implemented by the data layer and by tests. */
interface EngineEnvironment {
    fun resumableContexts(): List<ContextCapsule>
    fun appVersion(packageName: String): Long?
    fun artifactAvailable(artifact: Artifact): Boolean?
    fun usageBetween(from: Long, to: Long): List<AppUsage>
    val useCase: UseCase
    val showInterruptionAppNames: Boolean
    val depthPreference: DepthPreference
    val demoMode: Boolean get() = false
}

sealed interface EngineAction {
    data class SaveContext(val capsule: ContextCapsule, val isNew: Boolean, val askIntent: Boolean) : EngineAction
    data class OfferWarmStart(val warmStart: WarmStart, val match: MatchResult) : EngineAction
    data class InboxCandidate(val contextId: String, val score: Double) : EngineAction
    data class SilentReattach(val contextId: String) : EngineAction
    data class Span(val span: ReentryTracker.Span) : EngineAction
}

/**
 * The full automatic loop: segment → capsule → return match → delta → depth. Pure and replayable, so
 * the demo fixture and production collectors share exactly one code path.
 */
class ContextEngine(
    val config: EngineConfig,
    labelOf: (String) -> String,
    isTracked: (String) -> Boolean,
    private val newId: () -> String,
) {
    private val segmenter = SessionSegmenter(config, isTracked)
    private val builder = CapsuleBuilder(config, labelOf)
    private val matcher = ReturnMatcher(config)
    private val deltaBuilder = DeltaBuilder(labelOf)
    private val depth = DepthSelector(config)

    fun ingest(
        state0: SegmenterState,
        events: List<WorkEvent>,
        now: Long,
        env: EngineEnvironment,
    ): Pair<SegmenterState, List<EngineAction>> {
        val working = env.resumableContexts().associateBy { it.id }.toMutableMap()
        val actions = mutableListOf<EngineAction>()
        var state = state0

        fun handle(outputs: List<SegmentOutput>) {
            for (o in outputs) when (o) {
                is SegmentOutput.Finalized -> {
                    val existing = o.draft.attachedContextId?.let { working[it] }
                    val primary = o.draft.usage.firstOrNull()?.packageName ?: existing?.primaryApp
                    val capsule = builder.build(
                        id = existing?.id ?: newId(),
                        draft = o.draft,
                        existing = existing,
                        useCase = env.useCase,
                        primaryAppVersion = primary?.let(env::appVersion),
                        isDemo = env.demoMode,
                    )
                    working[capsule.id] = capsule
                    val ask = capsule.needsIntent || capsule.confidence < config.askIntentBelow
                    actions += EngineAction.SaveContext(capsule, isNew = existing == null, askIntent = ask)
                }

                is SegmentOutput.TrackedActivity -> {
                    val match = matcher.best(ReturnObservation(at = o.at, packageName = o.packageName), working.values.toList())
                        ?: continue
                    val ctx = working[match.contextId] ?: continue
                    val awayMs = o.at - (ctx.pausedAt ?: o.at)
                    val attach = { state = state.copy(session = state.session?.copy(attachedContextId = ctx.id)) }
                    when {
                        awayMs < config.silentReattachMs && match.band <= MatchBand.SUGGEST -> {
                            attach()
                            working[ctx.id] = ctx.copy(status = ContextStatus.ACTIVE)
                            actions += EngineAction.SilentReattach(ctx.id)
                        }
                        match.band == MatchBand.AUTO || match.band == MatchBand.SUGGEST -> {
                            if (match.band == MatchBand.AUTO) attach()
                            actions += EngineAction.OfferWarmStart(warmStart(ctx, o.at, match, env), match)
                        }
                        match.band == MatchBand.INBOX -> actions += EngineAction.InboxCandidate(ctx.id, match.score)
                        else -> Unit
                    }
                }

                is SegmentOutput.TrackedSpan -> actions += EngineAction.Span(ReentryTracker.Span(o.packageName, o.start, o.end))
            }
        }

        for (e in events.sortedBy { it.timestamp }) {
            val (s, out) = segmenter.process(state, e)
            state = s
            handle(out)
        }
        val (s, out) = segmenter.tick(state, now)
        state = s
        handle(out)
        return state to actions
    }

    fun warmStart(
        ctx: ContextCapsule,
        at: Long,
        match: MatchResult,
        env: EngineEnvironment,
        explicitFull: Boolean = false,
    ): WarmStart {
        val pausedAt = ctx.pausedAt ?: at
        val delta = deltaBuilder.build(
            ctx = ctx,
            now = at,
            awayUsage = env.usageBetween(pausedAt, at),
            showAppNames = env.showInterruptionAppNames,
            currentVersionOf = env::appVersion,
            artifactAvailable = env::artifactAvailable,
        )
        val level = depth.select(at - pausedAt, match.score, delta, env.depthPreference, explicitFull)
        return WarmStart(ctx.id, level, match.score, match.band, at - pausedAt, delta)
    }

    fun buildManual(
        now: Long,
        title: String,
        goal: String?,
        done: String?,
        blocker: String?,
        next: String?,
        link: Artifact?,
        useCase: UseCase,
    ): ContextCapsule = builder.manual(newId(), now, title, goal, done, blocker, next, link, useCase)

    fun pauseScore(state: SegmenterState, now: Long) = segmenter.pauseScore(state, now)
}
