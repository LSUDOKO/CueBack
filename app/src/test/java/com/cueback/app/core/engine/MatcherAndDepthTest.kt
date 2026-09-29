package com.cueback.app.core.engine

import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.DeltaItem
import com.cueback.app.core.model.DeltaKind
import com.cueback.app.core.model.MatchBand
import com.cueback.app.core.model.RecoveryLevel
import com.cueback.app.core.model.UseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MatcherAndDepthTest {
    private val config = EngineConfig()
    private val matcher = ReturnMatcher(config)
    private val builder = CapsuleBuilder(config, ::label)
    private val base = builder.manual("c1", T0, "JWT refresh", null, null, null, "Run test",
        Artifact(ArtifactType.URL, "https://auth0.com/docs/refresh", "Refresh docs", CHROME), UseCase.CODING,
    ).copy(primaryApp = TERMUX, apps = listOf(AppUsage(TERMUX, 10 * MIN), AppUsage(CHROME, 4 * MIN)))

    @Test fun `thresholds map to bands`() {
        assertEquals(MatchBand.AUTO, matcher.band(0.8))
        assertEquals(MatchBand.SUGGEST, matcher.band(0.6))
        assertEquals(MatchBand.INBOX, matcher.band(0.4))
        assertEquals(MatchBand.IGNORE, matcher.band(0.39))
    }

    @Test fun `same primary app soon after pause is auto`() {
        val r = matcher.score(ReturnObservation(T0 + 30 * MIN, TERMUX), base)
        assertEquals(MatchBand.AUTO, r.band)
    }

    @Test fun `secondary app is only a suggestion`() {
        val r = matcher.score(ReturnObservation(T0 + 10 * MIN, CHROME), base)
        assertEquals(MatchBand.SUGGEST, r.band)
    }

    @Test fun `stale context decays out of auto`() {
        assertEquals(MatchBand.SUGGEST, matcher.score(ReturnObservation(T0 + 30 * 60 * MIN, TERMUX), base).band)
        assertEquals(MatchBand.INBOX, matcher.score(ReturnObservation(T0 + 4 * 24 * 60 * MIN, TERMUX), base).band)
    }

    @Test fun `same shared url matches strongly and domain partially`() {
        val exact = matcher.score(ReturnObservation(T0 + MIN, artifact = Artifact(ArtifactType.URL, "https://auth0.com/docs/refresh")), base)
        val domain = matcher.score(ReturnObservation(T0 + MIN, artifact = Artifact(ArtifactType.URL, "https://auth0.com/other")), base)
        assertTrue(exact.score > domain.score)
        assertEquals(MatchBand.AUTO, exact.band)
    }

    @Test fun `explicit selection always wins`() {
        assertEquals(1.0, matcher.score(ReturnObservation(T0 + 999 * MIN, WHATSAPP, explicitContextId = "c1"), base).score, 0.0)
    }

    @Test fun `unrelated app is ignored`() {
        assertEquals(null, matcher.best(ReturnObservation(T0 + 100 * 60 * MIN, WHATSAPP), listOf(base)))
    }

    @Test fun `depth bands by time away`() {
        val d = DepthSelector(config)
        assertEquals(RecoveryLevel.MICRO, d.select(3 * MIN, 0.95, emptyList()))
        assertEquals(RecoveryLevel.CARD, d.select(45 * MIN, 0.95, emptyList()))
        assertEquals(RecoveryLevel.FULL, d.select(180 * MIN, 0.95, emptyList()))
    }

    @Test fun `uncertainty and heavy change deepen recovery, concise preference reduces it`() {
        val d = DepthSelector(config)
        assertEquals(RecoveryLevel.CARD, d.select(3 * MIN, 0.65, emptyList()))
        val changes = List(3) { DeltaItem(DeltaKind.ARTIFACT_MISSING, "x", true) }
        assertEquals(RecoveryLevel.FULL, d.select(45 * MIN, 0.95, changes))
        assertEquals(RecoveryLevel.MICRO, d.select(45 * MIN, 0.95, emptyList(), DepthPreference.CONCISE))
        assertEquals(RecoveryLevel.FULL, d.select(1 * MIN, 0.95, emptyList(), explicitFull = true))
    }

    @Test fun `delta hides app names unless allowed and reports missing files`() {
        val ctx = base.copy(
            pausedAt = T0, primaryAppVersion = 7L,
            artifacts = base.artifacts + Artifact(ArtifactType.FILE, "content://x/report.pdf", "report.pdf"),
        )
        val db = DeltaBuilder(::label)
        val hidden = db.build(ctx, T0 + 40 * MIN, listOf(AppUsage(WHATSAPP, 20 * MIN)), false, { 7L }, { false })
        assertFalse(hidden.any { it.text.contains("WhatsApp") })
        assertTrue(hidden.any { it.kind == DeltaKind.INTERRUPTIONS && it.text == "20 min in 1 other app" })
        assertTrue(hidden.any { it.kind == DeltaKind.ARTIFACT_MISSING && it.changed })
        assertTrue(hidden.any { it.kind == DeltaKind.APP_UNCHANGED && !it.changed })
        val shown = db.build(ctx, T0 + 40 * MIN, listOf(AppUsage(WHATSAPP, 20 * MIN)), true, { null }, { null })
        assertTrue(shown.any { it.text.contains("WhatsApp") })
        assertTrue(shown.any { it.kind == DeltaKind.APP_MISSING })
        val neverInstalled = db.build(ctx.copy(primaryAppVersion = null), T0 + 40 * MIN, emptyList(), true, { null }, { null })
        assertFalse("can't claim uninstalled if it was never seen installed", neverInstalled.any { it.kind == DeltaKind.APP_MISSING })
    }

    @Test fun `duration formatting`() {
        assertEquals("18s", DeltaBuilder.formatDuration(18_000))
        assertEquals("45 min", DeltaBuilder.formatDuration(45 * MIN))
        assertEquals("2h", DeltaBuilder.formatDuration(120 * MIN))
        assertEquals("2h 5m", DeltaBuilder.formatDuration(125 * MIN))
        assertEquals("3 days", DeltaBuilder.formatDuration(3 * 24 * 60 * MIN))
    }
}
