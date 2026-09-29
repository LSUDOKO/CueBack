package com.cueback.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import com.cueback.app.ai.AiRefiner
import com.cueback.app.ai.SecretStore
import com.cueback.app.billing.BillingRepository
import com.cueback.app.core.engine.CapsuleBuilder
import com.cueback.app.core.engine.EngineConfig
import com.cueback.app.data.db.CueBackDatabase
import com.cueback.app.data.repo.AnalyticsRepository
import com.cueback.app.data.repo.ContextRepository
import com.cueback.app.data.repo.DataPortability
import com.cueback.app.data.repo.Metric
import com.cueback.app.data.repo.ReentryRepository
import com.cueback.app.data.repo.SettingsRepository
import com.cueback.app.detect.DetectionCoordinator
import com.cueback.app.notify.Notifier
import com.cueback.app.notify.PushService
import com.cueback.app.platform.AppCatalog
import com.cueback.app.platform.ArtifactLauncher
import com.cueback.app.platform.UsageCollector
import java.util.UUID

/** Manual dependency graph; one instance per process. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val clock: () -> Long = System::currentTimeMillis
    val newId: () -> String = { "ctx_" + UUID.randomUUID().toString().replace("-", "").take(20) }

    val db: CueBackDatabase = CueBackDatabase.build(app)
    /** One DataStore per process: the container itself is a process singleton owned by [CueBackApp]. */
    val settings = SettingsRepository(PreferenceDataStoreFactory.create { app.preferencesDataStoreFile("cueback_settings") })
    val contexts = ContextRepository(db, clock)
    val reentry = ReentryRepository(db) { "re_" + UUID.randomUUID().toString().replace("-", "").take(20) }
    val catalog = AppCatalog(app)
    val usage = UsageCollector(app)
    val notifier = Notifier(app)
    val push = PushService(app)
    val billing = BillingRepository(app)
    val launcher = ArtifactLauncher(app, catalog)
    val secrets = SecretStore(app)
    val ai = AiRefiner(secrets)
    val engineConfig = EngineConfig()
    val builder = CapsuleBuilder(engineConfig, catalog::labelOf)

    val analytics = AnalyticsRepository(db, clock) { metric, value ->
        when (metric) {
            Metric.PURCHASE_COMPLETED, Metric.TRIAL_STARTED, Metric.WARM_START_ACCEPTED -> push.outcome(metric.name.lowercase(), value?.toFloat())
            else -> Unit
        }
    }

    val detection = DetectionCoordinator(
        appContext = app,
        db = db,
        settings = settings,
        contexts = contexts,
        reentry = reentry,
        usage = usage,
        catalog = catalog,
        notifier = notifier,
        push = push,
        billing = billing,
        analytics = analytics,
        clock = clock,
        newId = newId,
        config = engineConfig,
    )

    val portability = DataPortability(app, db, settings, contexts, reentry, secrets, notifier, push, clock)
}
