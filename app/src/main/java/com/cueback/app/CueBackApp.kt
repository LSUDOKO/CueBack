package com.cueback.app

import android.app.Application
import androidx.work.Configuration
import com.cueback.app.detect.LiveDetectionService
import com.cueback.app.detect.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CueBackApp : Application(), Configuration.Provider {
    lateinit var container: AppContainer
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** On-demand WorkManager initialization (the default startup initializer is removed in the manifest). */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.createChannels()
        appScope.launch {
            val userId = container.settings.localUserId()
            container.billing.configure(userId)
            container.billing.refresh()
            container.push.init(userId)
            val s = container.settings.current()
            if (s.onboarded) {
                Scheduler.schedule(this@CueBackApp)
                if (s.liveDetection && !s.collectionPaused && s.trackedApps.isNotEmpty()) {
                    runCatching { LiveDetectionService.start(this@CueBackApp) }
                }
            }
        }
    }
}
