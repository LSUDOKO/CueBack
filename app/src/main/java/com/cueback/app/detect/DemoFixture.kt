package com.cueback.app.detect

import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.NoteKind
import com.cueback.app.core.model.WorkEvent

/**
 * A recorded structured trace of the JWT refresh-bug story. It is replayed through the real engine,
 * persistence, and UI — only the input events are fixed. Demo contexts are flagged and deletable.
 */
data class DemoFixture(
    val tracked: Set<String>,
    val workEvents: List<WorkEvent>,
    val pausedCheckAt: Long,
    val returnEvent: WorkEvent,
    val awayUsage: List<AppUsage>,
) {
    companion object {
        const val BROWSER = "com.android.chrome"
        const val TERMINAL = "com.termux"
        const val CHAT = "com.whatsapp"
        private const val MIN = 60_000L

        fun jwtRefresh(now: Long): DemoFixture {
            val start = now - 75 * MIN
            val docs = Artifact(
                ArtifactType.URL,
                "https://datatracker.ietf.org/doc/html/rfc6749#section-6",
                "RFC 6749 §6 — Refreshing an Access Token",
                BROWSER,
                start + 4 * MIN,
            )
            val events = listOf(
                WorkEvent(start, EventType.APP_FOCUSED, packageName = BROWSER),
                WorkEvent(start + 4 * MIN, EventType.SHARE_RECEIVED, artifact = docs),
                WorkEvent(start + 6 * MIN, EventType.APP_FOCUSED, packageName = TERMINAL),
                WorkEvent(start + 17 * MIN, EventType.MANUAL_NOTE, noteKind = NoteKind.GOAL, note = "Fix JWT refresh so expired tokens renew silently"),
                WorkEvent(start + 18 * MIN, EventType.MANUAL_NOTE, noteKind = NoteKind.DONE, note = "Added refresh interceptor in authMiddleware.go"),
                WorkEvent(start + 18 * MIN + 5_000, EventType.MANUAL_NOTE, noteKind = NoteKind.BLOCKER, note = "Expired-token request still returns 401"),
                WorkEvent(start + 18 * MIN + 10_000, EventType.MANUAL_NOTE, noteKind = NoteKind.NEXT, note = "Run the expired-token test in auth/refresh_test.go"),
                WorkEvent(start + 20 * MIN, EventType.APP_FOCUSED, packageName = BROWSER),
                WorkEvent(start + 24 * MIN, EventType.APP_FOCUSED, packageName = "~other"),
            )
            return DemoFixture(
                tracked = setOf(BROWSER, TERMINAL),
                workEvents = events,
                pausedCheckAt = start + 40 * MIN,
                returnEvent = WorkEvent(now, EventType.APP_FOCUSED, packageName = TERMINAL),
                awayUsage = listOf(AppUsage(CHAT, 32 * MIN), AppUsage("com.google.android.youtube", 14 * MIN)),
            )
        }
    }
}
