package com.cueback.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.cueback.app.billing.BillingRepository
import com.cueback.app.core.model.AppUsage
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.core.model.EventType
import com.cueback.app.core.model.NoteKind
import com.cueback.app.core.model.Provenance
import com.cueback.app.core.model.RecoveryLevel
import com.cueback.app.core.model.WorkEvent
import com.cueback.app.data.db.CueBackDatabase
import com.cueback.app.data.repo.AnalyticsRepository
import com.cueback.app.data.repo.ContextRepository
import com.cueback.app.data.repo.LibraryFilter
import com.cueback.app.data.repo.Metric
import com.cueback.app.data.repo.ReentryOutcome
import com.cueback.app.data.repo.ReentryRepository
import com.cueback.app.data.repo.SettingsRepository
import com.cueback.app.data.repo.TimeWindow
import com.cueback.app.data.repo.WarmStartSource
import com.cueback.app.detect.DetectionCoordinator
import com.cueback.app.notify.Notifier
import com.cueback.app.notify.PushService
import com.cueback.app.platform.AppCatalogApi
import com.cueback.app.platform.OTHER_APP
import com.cueback.app.platform.UsageSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

private const val TERMUX = "com.termux"
private const val MIN = 60_000L

/** Scripted platform: what UsageStats would report, filtered exactly like the real collector. */
private class ScriptedUsage : UsageSource {
    var granted = true
    val focus = mutableListOf<Pair<Long, String>>()
    var away: List<AppUsage> = emptyList()
    var queried = 0
    override fun hasPermission() = granted
    override fun events(from: Long, to: Long, isTracked: (String) -> Boolean): List<WorkEvent> {
        queried++
        return focus.filter { it.first in from..to }.map { (t, p) ->
            WorkEvent(t, EventType.APP_FOCUSED, packageName = if (isTracked(p)) p else OTHER_APP)
        }
    }
    override fun usageBetween(from: Long, to: Long) = away
}

private object Labels : AppCatalogApi {
    override fun labelOf(pkg: String) = if (pkg == TERMUX) "Termux" else pkg
    override fun versionOf(pkg: String) = 7L
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class EndToEndTest {
    private lateinit var db: CueBackDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var contexts: ContextRepository
    private lateinit var reentry: ReentryRepository
    private lateinit var analytics: AnalyticsRepository
    private lateinit var coordinator: DetectionCoordinator
    private val usage = ScriptedUsage()
    private var now = 1_800_000_000_000L
    private var ids = 0
    private lateinit var storeFile: File

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, CueBackDatabase::class.java).allowMainThreadQueries().build()
        storeFile = File(ctx.filesDir, "test_${System.nanoTime()}.preferences_pb")
        settings = SettingsRepository(PreferenceDataStoreFactory.create { storeFile })
        contexts = ContextRepository(db, { now })
        reentry = ReentryRepository(db) { "re_${++ids}" }
        analytics = AnalyticsRepository(db, { now })
        coordinator = DetectionCoordinator(
            ctx, db, settings, contexts, reentry, usage, Labels, Notifier(ctx), PushService(ctx), BillingRepository(ctx), analytics,
            clock = { now }, newId = { "ctx_${++ids}" },
        )
    }

    @After fun tearDown() { db.close(); storeFile.delete() }

    private suspend fun configure() {
        settings.update { it.copy(onboarded = true, trackedApps = setOf(TERMUX)) }
        settings.setUsageCursor(now - 1)
    }

    @Test fun `work, interruption, auto context, return, warm start, meaningful action, re-entry time`() = runTest {
        configure()
        val t0 = now
        // Work in the tracked app, state the next step, then get pulled into another app.
        usage.focus += t0 to TERMUX
        now = t0 + 10 * MIN
        coordinator.runPass()
        coordinator.addNote(null, NoteKind.BLOCKER, "Expired-token request returns 401")
        coordinator.addNote(null, NoteKind.NEXT, "Run the expired-token test")
        usage.focus += (t0 + 20 * MIN) to "com.whatsapp"
        now = t0 + 26 * MIN
        coordinator.runPass()

        val ctx = contexts.open().single()
        assertEquals(ContextStatus.PAUSED, ctx.status)
        assertEquals(t0 + 20 * MIN, ctx.pausedAt)
        assertEquals(Provenance.USER, ctx.nextAction!!.provenance)
        assertEquals(TERMUX, ctx.primaryApp)
        assertEquals(1, analytics.count(Metric.AUTO_CONTEXT_CREATED))
        assertTrue("untracked app identity never stored", db.events().all().none { it.packageName == "com.whatsapp" })

        // Return to the same app 40 minutes later.
        usage.away = listOf(AppUsage("com.whatsapp", 38 * MIN))
        val back = t0 + 60 * MIN
        usage.focus += back to TERMUX
        now = back + 1_000
        coordinator.runPass()
        val suggestion = coordinator.suggestion.value
        assertNotNull(suggestion)
        assertEquals(ctx.id, suggestion!!.contextId)
        assertEquals(ContextStatus.ACTIVE, contexts.get(ctx.id)!!.status)

        // User opens CueBack (untracked from the engine's point of view) and sees the warm start.
        usage.focus += (back + 2_000) to "com.cueback.app"
        now = back + 3_000
        val view = coordinator.warmStartView(ctx.id)!!
        assertEquals(RecoveryLevel.CARD, view.warmStart.level)
        assertTrue(view.warmStart.delta.any { it.text.contains("other app") })
        val sid = coordinator.onWarmStartShown(ctx.id, view.warmStart.level, WarmStartSource.NOTIFICATION)
        now = back + 5_000
        coordinator.onWarmStartAccepted(sid, ctx.id)

        // Back in the tool at +9s; after 15s of sustained work the re-entry is recorded.
        usage.focus += (back + 9_000) to TERMUX
        now = back + 12_000
        coordinator.runPass()
        assertEquals(ReentryOutcome.ACCEPTED, reentry.get(sid)!!.outcome)
        now = back + 26_000
        coordinator.runPass()
        val rec = reentry.get(sid)!!
        assertEquals(ReentryOutcome.COMPLETED, rec.outcome)
        assertEquals(6_000L, rec.session.reentryMs)
        assertEquals(6.0, db.analytics().all().last { it.name == Metric.REENTRY_COMPLETED.name }.value!!, 0.0)
        assertNotNull(coordinator.completedReentry.value)
    }

    @Test fun `paused collection observes nothing`() = runTest {
        configure()
        settings.update { it.copy(collectionPaused = true) }
        usage.focus += now to TERMUX
        now += 30 * MIN
        coordinator.runPass()
        assertEquals(0, usage.queried)
        assertEquals(0, db.events().count())
        assertTrue(contexts.open().isEmpty())
    }

    @Test fun `no tracked apps or no permission observes nothing`() = runTest {
        settings.update { it.copy(onboarded = true) }
        usage.focus += now to TERMUX
        now += 30 * MIN
        coordinator.runPass()
        settings.update { it.copy(trackedApps = setOf(TERMUX)) }
        usage.granted = false
        coordinator.runPass()
        assertEquals(0, usage.queried)
    }

    @Test fun `free plan archives the oldest context beyond three`() = runTest {
        configure()
        repeat(4) { i ->
            coordinator.saveManual(com.cueback.app.core.engine.CapsuleBuilder(coordinator.config, Labels::labelOf)
                .manual("m$i", now + i, "Task $i", null, null, null, "Next $i", null, com.cueback.app.core.model.UseCase.GENERAL))
        }
        val open = contexts.open()
        assertEquals(3, open.size)
        assertFalse(open.any { it.id == "m0" })
        assertEquals(ContextStatus.ARCHIVED, contexts.get("m0")!!.status)
        assertTrue(coordinator.archivedForLimit.value)
    }

    @Test fun `search works offline across title, next action and links`() = runTest {
        configure()
        val b = com.cueback.app.core.engine.CapsuleBuilder(coordinator.config, Labels::labelOf)
        coordinator.saveManual(b.manual("a", now, "Compiler notes", null, null, null, "Read chapter on SSA", null, com.cueback.app.core.model.UseCase.STUDY))
        coordinator.saveManual(b.manual("b", now, "Essay", null, null, null, "Write intro",
            com.cueback.app.core.model.Artifact(com.cueback.app.core.model.ArtifactType.URL, "https://arxiv.org/abs/1234"), com.cueback.app.core.model.UseCase.WRITING))
        assertEquals(listOf("a"), contexts.search(LibraryFilter(query = "chapter")).first().map { it.id })
        assertEquals(listOf("b"), contexts.search(LibraryFilter(query = "arxiv")).first().map { it.id })
        assertEquals(listOf("b"), contexts.search(LibraryFilter(useCase = com.cueback.app.core.model.UseCase.WRITING)).first().map { it.id })
        assertEquals(2, contexts.search(LibraryFilter(window = TimeWindow.TODAY)).first().size)
        assertEquals(0, contexts.search(LibraryFilter(window = TimeWindow.YESTERDAY)).first().size)
        contexts.setStatus("a", ContextStatus.COMPLETED)
        assertEquals(listOf("a"), contexts.search(LibraryFilter(completedOnly = true)).first().map { it.id })
        assertEquals(listOf("b"), contexts.search(LibraryFilter(unresolvedOnly = true)).first().map { it.id })
    }

    @Test fun `delete all data leaves nothing behind`() = runTest {
        configure()
        coordinator.saveManual(com.cueback.app.core.engine.CapsuleBuilder(coordinator.config, Labels::labelOf)
            .manual("x", now, "T", null, null, null, "N", null, com.cueback.app.core.model.UseCase.GENERAL))
        reentry.start("x", now, RecoveryLevel.CARD, WarmStartSource.HOME)
        db.clearAllTables()
        settings.clearAll()
        assertTrue(contexts.all().isEmpty())
        assertTrue(reentry.all().isEmpty())
        assertTrue(db.analytics().all().isEmpty())
        assertTrue(settings.current().trackedApps.isEmpty())
        assertFalse(settings.current().onboarded)
    }

    @Test fun `demo replay goes through the real engine and is flagged`() = runTest {
        configure()
        val id = coordinator.replayDemo()
        assertNotNull(id)
        val c = contexts.get(id!!)!!
        assertTrue(c.isDemo)
        assertEquals("Run the expired-token test in auth/refresh_test.go", c.nextAction!!.text)
        assertEquals(id, coordinator.suggestion.value?.contextId)
        contexts.deleteDemo()
        assertNull(contexts.get(id))
    }
}
