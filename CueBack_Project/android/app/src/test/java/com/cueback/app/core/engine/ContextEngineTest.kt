package com.cueback.app.core.engine

import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.core.model.DeltaKind
import com.cueback.app.core.model.MatchBand
import com.cueback.app.core.model.NoteKind
import com.cueback.app.core.model.Provenance
import com.cueback.app.core.model.RecoveryLevel
import com.cueback.app.core.model.ReentrySession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextEngineTest {
    private var ids = 0
    private val engine = ContextEngine(EngineConfig(), ::label, { it in TRACKED }) { "ctx_${++ids}" }

    private fun saved(actions: List<EngineAction>) = actions.filterIsInstance<EngineAction.SaveContext>()

    @Test fun `interrupted session becomes a capsule with grounded facts`() {
        val env = FakeEnv()
        val (_, actions) = engine.ingest(SegmenterState(), jwtSession(), T0 + 40 * MIN, env)
        val save = saved(actions).single()
        val c = save.capsule
        assertTrue(save.isNew)
        assertEquals("Refresh Tokens — JWT docs", c.title.text)
        assertEquals(Provenance.INFERRED, c.title.provenance)
        assertEquals(Provenance.USER, c.nextAction!!.provenance)
        assertEquals("Run the expired-token test in auth/refresh_test.go", c.nextAction!!.text)
        assertEquals("Expired-token request returns 401", c.blocker!!.text)
        assertEquals(listOf("Added refresh interceptor"), c.completed.map { it.text })
        assertEquals(TERMUX, c.primaryApp)
        assertEquals(Provenance.DETECTED, c.currentState!!.provenance)
        assertEquals(T0 + 24 * MIN, c.pausedAt)
        assertEquals(100L.takeIf { c.primaryApp == CHROME } ?: 7L, c.primaryAppVersion)
        assertTrue(c.confidence > 0.8)
        assertFalse(save.askIntent)
        assertTrue(c.evidence.any { it.startsWith("Detected: shared") })
    }

    @Test fun `no next action is invented without evidence and intent is requested`() {
        val env = FakeEnv()
        val events = listOf(focus(T0, TERMUX), focus(T0 + 12 * MIN, WHATSAPP))
        val save = saved(engine.ingest(SegmenterState(), events, T0 + 30 * MIN, env).second).single()
        assertNull(save.capsule.nextAction)
        assertTrue(save.askIntent)
        assertEquals("Work in Termux", save.capsule.title.text)
        assertEquals(Provenance.DETECTED, save.capsule.title.provenance)
    }

    @Test fun `next action falls back to reopening shared artifact and is marked inferred`() {
        val env = FakeEnv()
        val events = listOf(focus(T0, CHROME), share(T0 + 2 * MIN, "https://kotlinlang.org/docs/coroutines.html", "Coroutines guide"), focus(T0 + 9 * MIN, WHATSAPP))
        val c = saved(engine.ingest(SegmenterState(), events, T0 + 30 * MIN, env).second).single().capsule
        assertEquals(Provenance.INFERRED, c.nextAction!!.provenance)
        assertTrue(c.nextAction!!.text.startsWith("Reopen “Coroutines guide”"))
    }

    @Test fun `full loop - return is matched, delta built, session attached and context evolves`() {
        val env = FakeEnv()
        var (state, actions) = engine.ingest(SegmenterState(), jwtSession(), T0 + 40 * MIN, env)
        val capsule = saved(actions).single().capsule
        env.contexts = mutableListOf(capsule)
        env.usage = listOf(AppUsage(WHATSAPP, 30 * MIN))
        env.versions[TERMUX] = 8L

        val back = T0 + 70 * MIN
        val r = engine.ingest(state, listOf(focus(back, TERMUX)), back + 1000, env)
        state = r.first
        val offer = r.second.filterIsInstance<EngineAction.OfferWarmStart>().single()
        assertEquals(MatchBand.AUTO, offer.match.band)
        assertEquals(capsule.id, offer.warmStart.contextId)
        assertEquals(46 * MIN, offer.warmStart.awayMs)
        assertEquals(RecoveryLevel.CARD, offer.warmStart.level)
        assertTrue(offer.warmStart.delta.any { it.kind == DeltaKind.INTERRUPTIONS && it.text.contains("WhatsApp") })
        assertTrue(offer.warmStart.delta.any { it.kind == DeltaKind.APP_UPDATED })
        assertEquals(capsule.id, state.session!!.attachedContextId)

        // Work continues, then another pause: same context evolves instead of a new one appearing.
        val later = engine.ingest(state, listOf(note(back + 5 * MIN, NoteKind.DONE, "Expired-token test passes"), pause(back + 6 * MIN)), back + 6 * MIN, env)
        val evolved = saved(later.second).single()
        assertFalse(evolved.isNew)
        assertEquals(capsule.id, evolved.capsule.id)
        assertEquals(2, evolved.capsule.completed.size)
        assertEquals(capsule.createdAt, evolved.capsule.createdAt)
    }

    @Test fun `quick return reattaches silently`() {
        val env = FakeEnv()
        val (state, actions) = engine.ingest(SegmenterState(), listOf(focus(T0, CHROME), pause(T0 + 5 * MIN)), T0 + 5 * MIN, env)
        env.contexts = mutableListOf(saved(actions).single().capsule)
        val r = engine.ingest(state, listOf(focus(T0 + 6 * MIN, CHROME)), T0 + 6 * MIN, env)
        assertTrue(r.second.single { it !is EngineAction.Span } is EngineAction.SilentReattach)
        assertNotNull(r.first.session!!.attachedContextId)
    }

    @Test fun `ambiguous contexts in the same app are only suggested`() {
        val env = FakeEnv()
        val a = saved(engine.ingest(SegmenterState(), listOf(focus(T0, CHROME), pause(T0 + 5 * MIN)), T0 + 5 * MIN, env).second).single().capsule
        val b = saved(engine.ingest(SegmenterState(), listOf(focus(T0 + 6 * MIN, CHROME), pause(T0 + 12 * MIN)), T0 + 12 * MIN, env).second).single().capsule
        env.contexts = mutableListOf(a, b)
        val offer = engine.ingest(SegmenterState(), listOf(focus(T0 + 60 * MIN, CHROME)), T0 + 60 * MIN, env).second
            .filterIsInstance<EngineAction.OfferWarmStart>().singleOrNull()
        val inbox = engine.ingest(SegmenterState(), listOf(focus(T0 + 60 * MIN, CHROME)), T0 + 60 * MIN, env).second
            .filterIsInstance<EngineAction.InboxCandidate>().singleOrNull()
        assertTrue("never auto-open when ambiguous", offer == null || offer.match.band != MatchBand.AUTO)
        assertTrue(offer != null || inbox != null)
    }

    @Test fun `different app does not trigger a warm start`() {
        val env = FakeEnv()
        env.contexts = mutableListOf(saved(engine.ingest(SegmenterState(), listOf(focus(T0, CHROME), pause(T0 + 5 * MIN)), T0 + 5 * MIN, env).second).single().capsule)
        val actions = engine.ingest(SegmenterState(), listOf(focus(T0 + 60 * MIN, TERMUX)), T0 + 60 * MIN, env).second
        assertTrue(actions.none { it is EngineAction.OfferWarmStart && it.match.band == MatchBand.AUTO })
    }

    @Test fun `completed or muted contexts are never matched`() {
        val env = FakeEnv()
        val c = saved(engine.ingest(SegmenterState(), listOf(focus(T0, CHROME), pause(T0 + 5 * MIN)), T0 + 5 * MIN, env).second).single().capsule
        env.contexts = mutableListOf(c.copy(status = ContextStatus.COMPLETED), c.copy(id = "m", muted = true))
        val actions = engine.ingest(SegmenterState(), listOf(focus(T0 + 60 * MIN, CHROME)), T0 + 60 * MIN, env).second
        assertTrue(actions.none { it is EngineAction.OfferWarmStart || it is EngineAction.InboxCandidate })
    }

    @Test fun `manual context is fully user provenance`() {
        val c = engine.buildManual(T0, "Essay intro", "Argue for X", null, null, "Write paragraph 2", null, com.cueback.app.core.model.UseCase.WRITING)
        assertEquals(Provenance.USER, c.title.provenance)
        assertEquals(Provenance.USER, c.nextAction!!.provenance)
        assertEquals(ContextStatus.PAUSED, c.status)
    }

    @Test fun `re-entry measured from warm start to first sustained work span`() {
        val tracker = ReentryTracker(EngineConfig())
        val session = ReentrySession("r1", "c1", warmStartAt = T0)
        val spans = listOf(
            ReentryTracker.Span(TERMUX, T0 + 5_000, T0 + 9_000), // too short
            ReentryTracker.Span(WHATSAPP, T0 + 10_000, T0 + 60_000), // unrelated app
            ReentryTracker.Span(TERMUX, T0 + 18_000, T0 + 90_000),
        )
        val at = tracker.meaningfulActionAt(session, spans, setOf(TERMUX, CHROME), T0 + 100_000)
        assertEquals(T0 + 18_000, at)
        assertEquals(18_000L, session.copy(firstMeaningfulActionAt = at).reentryMs)
        assertEquals(T0 + 20_000, tracker.ongoingMeaningful(session, TERMUX, T0 + 20_000, setOf(TERMUX), T0 + 36_000))
        assertNull(tracker.ongoingMeaningful(session, TERMUX, T0 + 20_000, setOf(TERMUX), T0 + 30_000))
    }
}
