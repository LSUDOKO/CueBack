package com.cueback.app.data.repo

import android.content.Context
import com.cueback.app.ai.SecretStore
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.data.db.CueBackDatabase
import com.cueback.app.detect.LiveDetectionService
import com.cueback.app.detect.Scheduler
import com.cueback.app.notify.Notifier
import com.cueback.app.notify.PushService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.OutputStream

@Serializable
data class ExportedReentry(val contextId: String, val warmStartAt: Long, val firstMeaningfulActionAt: Long?, val reentrySeconds: Double?, val outcome: String)

@Serializable
data class ExportBundle(
    val format: String = "cueback-export/1",
    val exportedAt: Long,
    val contexts: List<ContextCapsule>,
    val reentrySessions: List<ExportedReentry>,
    val trackedApps: List<String>,
)

/** User-owned data rights: export everything as JSON, or delete everything. */
class DataPortability(
    private val context: Context,
    private val db: CueBackDatabase,
    private val settings: SettingsRepository,
    private val contexts: ContextRepository,
    private val reentry: ReentryRepository,
    private val secrets: SecretStore,
    private val notifier: Notifier,
    private val push: PushService,
    private val clock: () -> Long,
) {
    suspend fun exportJson(): String {
        val bundle = ExportBundle(
            exportedAt = clock(),
            contexts = contexts.all(),
            reentrySessions = reentry.all().map {
                ExportedReentry(it.session.contextId, it.session.warmStartAt, it.session.firstMeaningfulActionAt, it.session.reentryMs?.div(1000.0), it.outcome.name)
            },
            trackedApps = settings.current().trackedApps.sorted(),
        )
        return ContextRepository.json.encodeToString(ExportBundle.serializer(), bundle)
    }

    suspend fun exportTo(out: OutputStream) = withContext(Dispatchers.IO) {
        out.use { it.write(exportJson().toByteArray()) }
    }

    /** Deletes contexts, events, re-entry history, analytics, settings, secrets and stops all collection. */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        LiveDetectionService.stop(context)
        Scheduler.cancelAll(context)
        notifier.cancelAll()
        push.logoutAndClear()
        db.clearAllTables()
        settings.clearAll()
        secrets.clear()
    }
}
