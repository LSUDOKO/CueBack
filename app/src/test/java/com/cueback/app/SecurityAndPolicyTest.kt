package com.cueback.app

import com.cueback.app.ai.AiRefiner
import com.cueback.app.ai.AiSuggestion
import com.cueback.app.ai.Redactor
import com.cueback.app.billing.EntitlementState
import com.cueback.app.billing.FeatureGate
import com.cueback.app.core.engine.CapsuleBuilder
import com.cueback.app.core.engine.EngineConfig
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.Provenance
import com.cueback.app.core.model.RecoveryLevel
import com.cueback.app.core.model.UseCase
import com.cueback.app.data.repo.AppSettings
import com.cueback.app.notify.DeepLink
import com.cueback.app.notify.DeepLinks
import com.cueback.app.notify.NotificationKind
import com.cueback.app.notify.NotificationPolicy
import com.cueback.app.platform.ArtifactPolicy
import com.cueback.app.platform.LaunchTarget
import com.cueback.app.platform.OTHER_APP
import com.cueback.app.platform.RawUsage
import com.cueback.app.platform.UsageMapper
import com.cueback.app.ui.share.ShareParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkTest {
    @Test fun `parses own warm start and context links`() {
        assertEquals(DeepLink.WarmStart("ctx_abc123", "notification"), DeepLinks.parse(DeepLinks.warmStart("ctx_abc123")))
        assertEquals(DeepLink.WarmStart("ctx_1", "push"), DeepLinks.parse("cueback://warmstart/ctx_1?src=push"))
        assertEquals(DeepLink.ContextDetail("ctx_1"), DeepLinks.parse(DeepLinks.context("ctx_1")))
        assertEquals(DeepLink.Library, DeepLinks.parse("cueback://library"))
    }

    @Test fun `rejects foreign schemes, traversal, bad ids and unknown hosts`() {
        listOf(
            "https://evil.example/warmstart/x", "javascript:alert(1)", "intent://warmstart/x#Intent;end",
            "cueback://warmstart/../../etc", "cueback://warmstart/a/b", "cueback://warmstart/%2e%2e",
            "cueback://unknown/x", "cueback://warmstart/" + "a".repeat(80), "", null,
        ).forEach { assertNull(it, DeepLinks.parse(it)) }
    }

    @Test fun `unknown source falls back safely`() {
        assertEquals(DeepLink.WarmStart("c1", "notification"), DeepLinks.parse("cueback://warmstart/c1?src=evil"))
        assertNull(DeepLinks.parse("cueback://warmstart/c1?src=<script>"))
    }

    @Test fun `push payload uses validated context id then launch url`() {
        assertEquals(DeepLink.WarmStart("ctx_9", "push"), DeepLinks.fromPush("ctx_9", null))
        assertEquals(DeepLink.Library, DeepLinks.fromPush("bad id!", "cueback://library"))
        assertNull(DeepLinks.fromPush(null, "https://example.com"))
    }
}

class ArtifactPolicyTest {
    @Test fun `only safe targets launch`() {
        assertEquals(LaunchTarget.Web("https://a.com/x"), ArtifactPolicy.target(Artifact(ArtifactType.URL, "https://a.com/x")))
        assertNull(ArtifactPolicy.target(Artifact(ArtifactType.URL, "javascript:alert(1)")))
        assertNull(ArtifactPolicy.target(Artifact(ArtifactType.URL, "file:///data/data/x")))
        assertNull(ArtifactPolicy.target(Artifact(ArtifactType.URL, "intent://x#Intent;end")))
        assertNull(ArtifactPolicy.target(Artifact(ArtifactType.FILE, "file:///sdcard/secret")))
        assertNull(ArtifactPolicy.target(Artifact(ArtifactType.FILE, "content://x/../../y")))
        assertEquals(LaunchTarget.Content("content://docs/1"), ArtifactPolicy.target(Artifact(ArtifactType.FILE, "content://docs/1")))
        assertEquals(LaunchTarget.App("com.termux"), ArtifactPolicy.target(Artifact(ArtifactType.APP, "com.termux")))
        assertNull(ArtifactPolicy.target(Artifact(ArtifactType.APP, "com.x; rm -rf /")))
        assertNull(ArtifactPolicy.target(Artifact(ArtifactType.TEXT, "hello")))
    }

    @Test fun `extracts url from shared text`() {
        assertEquals("https://kotlinlang.org/docs", ArtifactPolicy.firstUrl("Kotlin docs https://kotlinlang.org/docs."))
        assertNull(ArtifactPolicy.firstUrl("no link here"))
    }
}

class ShareParserTest {
    @Test fun `browser share becomes url artifact with title`() {
        val a = ShareParser.parse("https://auth0.com/docs/refresh", "Refresh tokens", null, null, "com.android.chrome", 5)!!
        assertEquals(ArtifactType.URL, a.type)
        assertEquals("Refresh tokens", a.title)
        assertEquals("com.android.chrome", a.sourcePackage)
    }

    @Test fun `plain text and files`() {
        assertEquals(ArtifactType.TEXT, ShareParser.parse("remember the edge case", null, null, null, null, 0)!!.type)
        val f = ShareParser.parse(null, null, "content://media/1", "notes.pdf", null, 0)!!
        assertEquals(ArtifactType.FILE, f.type)
        assertEquals("notes.pdf", f.title)
        assertNull(ShareParser.parse(null, null, "file:///etc/passwd", null, null, 0))
        assertNull(ShareParser.parse("   ", null, null, null, null, 0))
    }
}

class RedactorTest {
    @Test fun `removes common secrets`() {
        val input = """
            Authorization: Bearer abcdefghijklmnop.qrstuv
            token=supersecretvalue123 api_key: sk_live_abcdefghijklmnopqrstuvwx
            eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U
            AKIAABCDEFGHIJKLMNOP ghp_abcdefghijklmnopqrstuvwxyz0123456789
            mail me@example.com https://x.com/cb?access_token=abc123&ok=1
        """.trimIndent()
        val out = Redactor.redact(input)
        listOf("abcdefghijklmnop", "supersecretvalue123", "sk_live_", "eyJhbGci", "AKIAABCD", "ghp_abc", "me@example.com", "abc123").forEach {
            assertFalse("leaked $it in $out", out.contains(it))
        }
        assertTrue(out.contains("ok=1"))
        assertTrue(Redactor.containsSecret(input))
        assertFalse(Redactor.containsSecret("Run the expired-token test"))
    }
}

class AiRefinerTest {
    private val builder = CapsuleBuilder(EngineConfig()) { it }

    @Test fun `ai never overwrites user facts and is labeled inferred`() {
        val c = builder.manual("c", 0, "My title", null, null, null, "My next step", null, UseCase.CODING)
        val out = AiRefiner(FakeSecrets).apply(c, AiSuggestion(title = "AI title", goal = "AI goal", next_action = "AI next"))
        assertEquals("My title", out.title.text)
        assertEquals("My next step", out.nextAction!!.text)
        assertEquals(Provenance.INFERRED, out.goal!!.provenance)
        assertTrue(out.aiRefined)
    }

    @Test fun `payload is redacted and minimized`() {
        val c = builder.manual("c", 0, "Fix token=hunter2secret", null, null, null, null,
            Artifact(ArtifactType.URL, "https://internal.corp/path?sig=abcdef", "Design doc"), UseCase.CODING)
        val payload = AiRefiner(FakeSecrets).minimizedPayload(c) { it }
        assertFalse(payload.contains("hunter2secret"))
        assertFalse("full URL must not be sent", payload.contains("/path"))
        assertTrue(payload.contains("internal.corp"))
    }

    @Test fun `parses openai style response and tolerates garbage`() {
        val body = """{"choices":[{"message":{"content":"{\"title\":\"T\",\"goal\":\"G\",\"next_action\":\"N\"}"}}]}"""
        assertEquals(AiSuggestion("T", "G", "N"), AiRefiner(FakeSecrets).parse(body))
        assertNull(AiRefiner(FakeSecrets).parse("<html>"))
    }

    @Test fun `ai disabled by default`() {
        assertFalse(AiRefiner(FakeSecrets).isReady(AppSettings()))
    }
}

class NotificationPolicyTest {
    private val base = AppSettings(onboarded = true)
    private val ctx = CapsuleBuilder(EngineConfig()) { it }.manual("c", 0, "T", null, null, null, "Next", null, UseCase.GENERAL)
    private val day = 24 * 60 * 60_000L

    @Test fun `quiet hours wrap past midnight`() {
        assertTrue(NotificationPolicy.inQuietHours(base, 23))
        assertTrue(NotificationPolicy.inQuietHours(base, 3))
        assertFalse(NotificationPolicy.inQuietHours(base, 12))
        assertFalse(NotificationPolicy.inQuietHours(base.copy(quietHoursEnabled = false), 23))
    }

    @Test fun `opt out, mute, quiet hours and cooldown suppress resume`() {
        val k = NotificationKind.RESUME_CANDIDATE
        assertTrue(NotificationPolicy.allowed(k, base, ctx, 12, 1_000_000, null, false))
        assertFalse(NotificationPolicy.allowed(k, base.copy(notificationsEnabled = false), ctx, 12, 1_000_000, null, false))
        assertFalse(NotificationPolicy.allowed(k, base, ctx.copy(muted = true), 12, 1_000_000, null, false))
        assertFalse(NotificationPolicy.allowed(k, base, ctx, 23, 1_000_000, null, false))
        assertFalse(NotificationPolicy.allowed(k, base, ctx, 12, 1_000_000, 1_000_000 - 60_000, false))
    }

    @Test fun `reminder only once, only for aged unresolved contexts, only on pro`() {
        val k = NotificationKind.UNRESOLVED_REMINDER
        val now = 10 * day
        val aged = ctx.copy(pausedAt = now - 2 * day)
        assertTrue(NotificationPolicy.allowed(k, base, aged, 12, now, null, true))
        assertFalse("free plan", NotificationPolicy.allowed(k, base, aged, 12, now, null, false))
        assertFalse("already reminded", NotificationPolicy.allowed(k, base, aged, 12, now, now - day, true))
        assertFalse("too fresh", NotificationPolicy.allowed(k, base, ctx.copy(pausedAt = now - 60_000), 12, now, null, true))
        assertFalse("done", NotificationPolicy.allowed(k, base, aged.copy(status = ContextStatus.COMPLETED), 12, now, null, true))
        assertFalse("no next", NotificationPolicy.allowed(k, base, aged.copy(nextAction = null), 12, now, null, true))
    }

    @Test fun `lock screen text has no task content`() {
        NotificationKind.entries.forEach { assertFalse(NotificationPolicy.publicText(it).contains("Next")) }
    }
}

class FeatureGateTest {
    @Test fun `free tier limits`() {
        listOf(EntitlementState.Free, EntitlementState.NotConfigured, EntitlementState.Loading, EntitlementState.Error("x")).forEach { s ->
            val g = FeatureGate(s)
            assertFalse(g.pro)
            assertTrue(g.canCreateContext(2))
            assertFalse(g.canCreateContext(3))
            assertEquals(RecoveryLevel.CARD, g.cap(RecoveryLevel.FULL))
            assertEquals(setOf("a.b"), g.effectiveTracked(setOf("c.d", "a.b")))
        }
    }

    @Test fun `pro unlocks everything`() {
        val g = FeatureGate(EntitlementState.Pro(isTrial = true, expiresAt = null, willRenew = true))
        assertTrue(g.canCreateContext(1000))
        assertEquals(RecoveryLevel.FULL, g.cap(RecoveryLevel.FULL))
        assertEquals(2, g.effectiveTracked(setOf("c.d", "a.b")).size)
    }
}

class UsageMapperTest {
    private val tracked = setOf("com.android.chrome")

    @Test fun `untracked apps and CueBack itself are anonymized`() {
        val raw = listOf(
            RawUsage(1, RawUsage.Kind.RESUMED, "com.android.chrome"),
            RawUsage(2, RawUsage.Kind.RESUMED, "com.cueback.app"),
            RawUsage(3, RawUsage.Kind.RESUMED, "com.whatsapp"),
            RawUsage(4, RawUsage.Kind.RESUMED, "com.instagram.android"),
            RawUsage(5, RawUsage.Kind.KEYGUARD_SHOWN, null),
            RawUsage(6, RawUsage.Kind.SCREEN_OFF, null),
            RawUsage(7, RawUsage.Kind.KEYGUARD_HIDDEN, null),
        )
        val events = UsageMapper.toWorkEvents(raw, "com.cueback.app", tracked::contains)
        assertEquals(listOf(EventType.APP_FOCUSED, EventType.APP_FOCUSED, EventType.DEVICE_LOCKED, EventType.DEVICE_UNLOCKED), events.map { it.type })
        assertEquals(listOf("com.android.chrome", OTHER_APP), events.take(2).map { it.packageName })
        assertTrue(events.none { it.packageName == "com.whatsapp" })
    }

    @Test fun `foreground durations`() {
        val raw = listOf(
            RawUsage(100, RawUsage.Kind.RESUMED, "a"),
            RawUsage(400, RawUsage.Kind.PAUSED, "a"),
            RawUsage(400, RawUsage.Kind.RESUMED, "b"),
            RawUsage(700, RawUsage.Kind.SCREEN_OFF, null),
        )
        val u = UsageMapper.foreground(raw, 0, 1000, "own").associate { it.packageName to it.foregroundMs }
        assertEquals(mapOf("a" to 300L, "b" to 300L), u)
    }
}

private object FakeSecrets : com.cueback.app.ai.SecretStoreApi {
    override fun has(name: String) = false
    override fun get(name: String): String? = null
}

