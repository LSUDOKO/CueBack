package com.cueback.app.core.engine

import com.cueback.app.core.model.BoundaryReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSegmenterTest {
    private val config = EngineConfig()
    private val seg = SessionSegmenter(config) { it in TRACKED }

    private fun run(events: List<com.cueback.app.core.model.WorkEvent>, now: Long): Pair<SegmenterState, List<SegmentOutput>> {
        var s = SegmenterState()
        val out = mutableListOf<SegmentOutput>()
        events.forEach { e -> seg.process(s, e).let { s = it.first; out += it.second } }
        seg.tick(s, now).let { s = it.first; out += it.second }
        return s to out
    }

    private fun finalized(out: List<SegmentOutput>) = out.filterIsInstance<SegmentOutput.Finalized>().map { it.draft }

    @Test fun `glance at untracked app does not end session`() {
        val (s, out) = run(listOf(focus(T0, CHROME), focus(T0 + 5 * MIN, WHATSAPP), focus(T0 + 5 * MIN + 20_000, CHROME)), T0 + 6 * MIN)
        assertTrue(finalized(out).isEmpty())
        assertNotNull(s.session)
        assertNull(s.awaySince)
    }

    @Test fun `untracked app alone finalizes only after idle threshold`() {
        val events = listOf(focus(T0, CHROME), focus(T0 + 10 * MIN, WHATSAPP))
        assertTrue(finalized(run(events, T0 + 14 * MIN).second).isEmpty())
        val drafts = finalized(run(events, T0 + 15 * MIN + 1).second)
        assertEquals(1, drafts.size)
        assertEquals(BoundaryReason.LEFT_TRACKED_APP, drafts[0].boundary)
        assertEquals("session ends when the user left, not when detected", T0 + 10 * MIN, drafts[0].endedAt)
        assertEquals(10 * MIN, drafts[0].usage.single().foregroundMs)
    }

    @Test fun `lock finalizes after grace period`() {
        val events = listOf(focus(T0, CHROME), lock(T0 + 8 * MIN))
        assertTrue(finalized(run(events, T0 + 8 * MIN + 10_000).second).isEmpty())
        val d = finalized(run(events, T0 + 8 * MIN + config.minAwayMs).second).single()
        assertEquals(BoundaryReason.DEVICE_LOCKED, d.boundary)
        assertEquals(0.6, d.pauseScore, 1e-9)
    }

    @Test fun `quick lock and unlock keeps session`() {
        val events = listOf(focus(T0, CHROME), lock(T0 + 8 * MIN), unlock(T0 + 8 * MIN + 10_000), focus(T0 + 8 * MIN + 12_000, CHROME))
        val (s, out) = run(events, T0 + 9 * MIN)
        assertTrue(finalized(out).isEmpty())
        assertNotNull(s.session)
    }

    @Test fun `explicit pause finalizes immediately`() {
        val d = finalized(run(listOf(focus(T0, CHROME), pause(T0 + 3 * MIN)), T0 + 3 * MIN).second).single()
        assertEquals(BoundaryReason.EXPLICIT_PAUSE, d.boundary)
        assertEquals(T0 + 3 * MIN, d.endedAt)
    }

    @Test fun `short session without evidence is discarded as noise`() {
        val out = run(listOf(focus(T0, CHROME), lock(T0 + 30_000)), T0 + 5 * MIN).second
        assertTrue(finalized(out).isEmpty())
    }

    @Test fun `short session with a user note is kept`() {
        val out = run(listOf(focus(T0, CHROME), note(T0 + 10_000, com.cueback.app.core.model.NoteKind.NEXT, "x"), lock(T0 + 30_000)), T0 + 5 * MIN).second
        assertEquals(1, finalized(out).size)
    }

    @Test fun `return after finalize emits tracked activity`() {
        val out = run(listOf(focus(T0, CHROME), lock(T0 + 8 * MIN), unlock(T0 + 50 * MIN), focus(T0 + 50 * MIN, CHROME)), T0 + 51 * MIN).second
        val activities = out.filterIsInstance<SegmentOutput.TrackedActivity>()
        assertEquals(listOf(T0, T0 + 50 * MIN), activities.map { it.at })
        assertEquals(1, finalized(out).size)
    }

    @Test fun `consecutive duplicate focus events are deduplicated`() {
        val d = finalized(run(listOf(focus(T0, CHROME), focus(T0 + MIN, CHROME), focus(T0 + 2 * MIN, CHROME), pause(T0 + 3 * MIN)), T0 + 3 * MIN).second).single()
        assertEquals(1, d.events.count { it.type == com.cueback.app.core.model.EventType.APP_FOCUSED })
        assertEquals(3 * MIN, d.usage.single().foregroundMs)
    }

    @Test fun `untracked app events never enter the session`() {
        val d = finalized(run(jwtSession(), T0 + 40 * MIN).second).single()
        assertTrue(d.events.none { it.packageName == WHATSAPP })
        assertTrue(d.usage.none { it.packageName == WHATSAPP })
    }
}
