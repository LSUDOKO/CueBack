package com.cueback.app.detect

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.cueback.app.CueBackApp
import com.cueback.app.notify.Notifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** Periodic fallback: segmentation and return detection keep working without the live service. */
class DetectionWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as CueBackApp
        app.container.detection.runPass()
        return Result.success()
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        (applicationContext as CueBackApp).container.detection.sendReminders()
        return Result.success()
    }
}

object Scheduler {
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            "detection",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DetectionWorker>(15, TimeUnit.MINUTES).build(),
        )
        wm.enqueueUniquePeriodicWork(
            "reminders",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    fun cancelAll(context: Context) = WorkManager.getInstance(context).cancelAllWork()
}

/**
 * Optional live detection: polls usage events every few seconds while the screen is on so returns are
 * recognized within seconds. It reads only structured usage metadata — no screen or audio capture.
 */
class LiveDetectionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> startLoop()
                Intent.ACTION_SCREEN_OFF -> {
                    loop?.cancel()
                    scope.launch { container().detection.runPass() }
                }
            }
        }
    }

    private fun container() = (application as CueBackApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tracked = runCatching { kotlinx.coroutines.runBlocking { container().settings.current().trackedApps.size } }.getOrDefault(0)
        ServiceCompat.startForeground(
            this,
            Notifier.SERVICE_ID,
            container().notifier.serviceNotification(tracked),
            if (android.os.Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        if (loop?.isActive == true) return
        val pm = getSystemService(PowerManager::class.java)
        loop = scope.launch {
            while (isActive) {
                val s = container().settings.settings.first()
                if (!s.liveDetection || s.collectionPaused) { stopSelf(); break }
                if (pm.isInteractive) runCatching { container().detection.runPass() }
                delay(POLL_MS)
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val POLL_MS = 8_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, LiveDetectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveDetectionService::class.java))
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Scheduler.schedule(context)
        val app = context.applicationContext as CueBackApp
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = app.container.settings.current()
                if (s.liveDetection && !s.collectionPaused && s.trackedApps.isNotEmpty()) {
                    runCatching { LiveDetectionService.start(context) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
