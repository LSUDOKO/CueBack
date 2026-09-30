package com.cueback.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cueback.app.AppContainer
import com.cueback.app.ai.SecretStore
import com.cueback.app.billing.FeatureGate
import com.cueback.app.core.engine.DepthPreference
import com.cueback.app.data.repo.AppSettings
import com.cueback.app.detect.LiveDetectionService
import com.cueback.app.detect.Scheduler
import com.cueback.app.platform.ArtifactPolicy
import com.cueback.app.platform.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared by onboarding, settings and the app picker. */
class SettingsViewModel(val c: AppContainer) : ViewModel() {
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val entitlement = c.billing.state

    private val _usageGranted = MutableStateFlow(c.usage.hasPermission())
    val usageGranted = _usageGranted.asStateFlow()

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps = _apps.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    val hasAiKey get() = c.secrets.has(SecretStore.AI_KEY)
    val billingConfigured get() = c.billing.configured
    val pushConfigured get() = c.push.configured

    fun refreshPermissions() { _usageGranted.value = c.usage.hasPermission() }

    fun loadApps() = viewModelScope.launch {
        _apps.value = withContext(Dispatchers.IO) { c.catalog.launchableApps() }
    }

    fun usageAccessIntent(ctx: Context) = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
        data = Uri.fromParts("package", ctx.packageName, null)
    }

    fun toggleApp(pkg: String, on: Boolean) = viewModelScope.launch {
        c.settings.update { it.copy(trackedApps = if (on) it.trackedApps + pkg else it.trackedApps - pkg) }
        c.detection.runPass()
    }

    fun update(f: (AppSettings) -> AppSettings) = viewModelScope.launch { c.settings.update(f) }

    fun setLiveDetection(ctx: Context, on: Boolean) = viewModelScope.launch {
        c.settings.update { it.copy(liveDetection = on) }
        if (on) runCatching { LiveDetectionService.start(ctx) }.onFailure { _message.value = "Couldn't start live detection: ${it.message}" }
        else LiveDetectionService.stop(ctx)
    }

    /** Pausing collection closes the open session immediately; nothing more is observed until resumed. */
    fun setCollectionPaused(ctx: Context, paused: Boolean) = viewModelScope.launch {
        if (paused) {
            c.detection.pauseCollection()
            c.settings.update { it.copy(collectionPaused = true) }
            LiveDetectionService.stop(ctx)
        } else {
            c.settings.update { it.copy(collectionPaused = false) }
            c.settings.setUsageCursor(c.clock())
            if (c.settings.current().liveDetection) runCatching { LiveDetectionService.start(ctx) }
        }
    }

    fun setNotifications(on: Boolean) = viewModelScope.launch {
        c.settings.update { it.copy(notificationsEnabled = on) }
        c.push.setOptOut(!on)
        if (!on) c.notifier.cancelAll()
    }

    fun setDepth(p: DepthPreference) = update { it.copy(depthPreference = p) }

    fun saveAi(enabled: Boolean, url: String, model: String, key: String?) = viewModelScope.launch {
        val safe = ArtifactPolicy.safeWebUrl(url)
        if (enabled && (safe == null || !safe.startsWith("https://"))) {
            _message.value = "Use an https endpoint for cloud AI."
            return@launch
        }
        if (!key.isNullOrBlank()) c.secrets.put(SecretStore.AI_KEY, key.trim())
        c.settings.update { it.copy(cloudAiEnabled = enabled, aiBaseUrl = safe ?: "", aiModel = model.trim()) }
        _message.value = if (enabled) "Cloud AI is on. Only redacted summaries are sent, and only when you tap Refine." else "Cloud AI is off. Nothing is sent."
    }

    fun clearAiKey() { c.secrets.put(SecretStore.AI_KEY, null); _message.value = "API key removed." }

    fun export(ctx: Context, uri: Uri) = viewModelScope.launch {
        runCatching {
            ctx.contentResolver.openOutputStream(uri)?.let { c.portability.exportTo(it) } ?: error("Couldn't open the file")
        }.onSuccess { _message.value = "Exported your CueBack data as JSON." }
            .onFailure { _message.value = "Export failed: ${it.message}" }
    }

    fun deleteAll(done: () -> Unit) = viewModelScope.launch {
        c.portability.deleteAll()
        c.detection.clearSuggestion()
        done()
    }

    /**
     * Records that onboarding is done and starts detection. It suspends until the setting is on disk,
     * so the caller can leave the screen only once that is true. It runs in the caller's scope, not
     * this view model's: a view model job that was cancelled would end without saving anything.
     */
    suspend fun completeOnboarding(ctx: Context) {
        c.settings.update { it.copy(onboarded = true) }
        c.settings.setUsageCursor(c.clock())
        runCatching { Scheduler.schedule(ctx) }
        val s = c.settings.current()
        if (s.liveDetection && s.trackedApps.isNotEmpty()) runCatching { LiveDetectionService.start(ctx) }
    }

    fun restorePurchases() = viewModelScope.launch {
        _message.value = when (val r = c.billing.restore()) {
            com.cueback.app.billing.PurchaseOutcome.Success -> "Pro restored."
            is com.cueback.app.billing.PurchaseOutcome.Failed -> r.message
            else -> null
        }
    }

    fun replayDemo(onReady: (String) -> Unit) = viewModelScope.launch { c.detection.replayDemo()?.let(onReady) }

    fun consumeMessage() { _message.value = null }
}
