package com.cueback.app.core.engine

import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.Artifact
import com.cueback.app.core.model.ArtifactType
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.NoteKind
import com.cueback.app.core.model.UseCase
import com.cueback.app.core.model.WorkEvent

const val CHROME = "com.android.chrome"
const val TERMUX = "com.termux"
const val WHATSAPP = "com.whatsapp"
const val MIN = 60_000L
const val T0 = 1_800_000_000_000L

val TRACKED = setOf(CHROME, TERMUX)
fun label(pkg: String) = when (pkg) {
    CHROME -> "Chrome"; TERMUX -> "Termux"; WHATSAPP -> "WhatsApp"; else -> pkg
}

fun focus(t: Long, pkg: String) = WorkEvent(t, EventType.APP_FOCUSED, packageName = pkg)
fun lock(t: Long) = WorkEvent(t, EventType.DEVICE_LOCKED)
fun unlock(t: Long) = WorkEvent(t, EventType.DEVICE_UNLOCKED)
fun pause(t: Long) = WorkEvent(t, EventType.EXPLICIT_PAUSE)
fun note(t: Long, kind: NoteKind, text: String) = WorkEvent(t, EventType.MANUAL_NOTE, noteKind = kind, note = text)
fun share(t: Long, url: String, title: String, from: String = CHROME) =
    WorkEvent(t, EventType.SHARE_RECEIVED, artifact = Artifact(ArtifactType.URL, url, title, from, t))

class FakeEnv(
    var contexts: MutableList<ContextCapsule> = mutableListOf(),
    val versions: MutableMap<String, Long> = mutableMapOf(CHROME to 100L, TERMUX to 7L),
    var usage: List<AppUsage> = emptyList(),
    override val useCase: UseCase = UseCase.CODING,
    override val showInterruptionAppNames: Boolean = true,
    override val depthPreference: DepthPreference = DepthPreference.AUTO,
) : EngineEnvironment {
    override fun resumableContexts() = contexts.toList()
    override fun appVersion(packageName: String) = versions[packageName]
    override fun artifactAvailable(artifact: Artifact): Boolean? = null
    override fun usageBetween(from: Long, to: Long) = usage
}

/** The canonical demo story: JWT refresh bug, studied in Chrome and tested in Termux. */
fun jwtSession(start: Long = T0): List<WorkEvent> = listOf(
    focus(start, CHROME),
    share(start + 4 * MIN, "https://auth0.com/docs/secure/tokens/refresh-tokens", "Refresh Tokens — JWT docs"),
    focus(start + 6 * MIN, TERMUX),
    note(start + 18 * MIN, NoteKind.DONE, "Added refresh interceptor"),
    note(start + 18 * MIN + 5_000, NoteKind.BLOCKER, "Expired-token request returns 401"),
    note(start + 18 * MIN + 10_000, NoteKind.NEXT, "Run the expired-token test in auth/refresh_test.go"),
    focus(start + 20 * MIN, CHROME),
    focus(start + 24 * MIN, WHATSAPP),
)
