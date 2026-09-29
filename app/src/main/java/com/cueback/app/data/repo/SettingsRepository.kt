package com.cueback.app.data.repo

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.cueback.app.core.engine.DepthPreference
import com.cueback.app.core.engine.SegmenterState
import com.cueback.app.core.model.UseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Privacy-first defaults: nothing is observed, nothing leaves the device, until the user opts in. */
data class AppSettings(
    val onboarded: Boolean = false,
    val useCase: UseCase = UseCase.GENERAL,
    val trackedApps: Set<String> = emptySet(),
    val collectionPaused: Boolean = false,
    val liveDetection: Boolean = false,
    val showInterruptionAppNames: Boolean = false,
    val depthPreference: DepthPreference = DepthPreference.AUTO,
    val notificationsEnabled: Boolean = true,
    val lockScreenPrivate: Boolean = true,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val quietHoursEnabled: Boolean = true,
    val remindersEnabled: Boolean = true,
    val cloudAiEnabled: Boolean = false,
    val aiBaseUrl: String = "",
    val aiModel: String = "",
    val hasSeenPaywall: Boolean = false,
)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val useCase = stringPreferencesKey("use_case")
        val tracked = stringSetPreferencesKey("tracked_apps")
        val paused = booleanPreferencesKey("collection_paused")
        val live = booleanPreferencesKey("live_detection")
        val appNames = booleanPreferencesKey("interruption_app_names")
        val depth = stringPreferencesKey("depth_pref")
        val notif = booleanPreferencesKey("notifications")
        val lockPrivate = booleanPreferencesKey("lock_private")
        val quietStart = intPreferencesKey("quiet_start")
        val quietEnd = intPreferencesKey("quiet_end")
        val quietOn = booleanPreferencesKey("quiet_on")
        val reminders = booleanPreferencesKey("reminders")
        val cloudAi = booleanPreferencesKey("cloud_ai")
        val aiUrl = stringPreferencesKey("ai_url")
        val aiModel = stringPreferencesKey("ai_model")
        val seenPaywall = booleanPreferencesKey("seen_paywall")
        val segmenter = stringPreferencesKey("segmenter_state")
        val cursor = longPreferencesKey("usage_cursor")
        val userId = stringPreferencesKey("local_user_id")
    }

    val settings: Flow<AppSettings> = store.data.map(::read)

    private fun read(p: Preferences): AppSettings =
        AppSettings(
            onboarded = p[K.onboarded] ?: false,
            useCase = p[K.useCase]?.let { runCatching { UseCase.valueOf(it) }.getOrNull() } ?: UseCase.GENERAL,
            trackedApps = p[K.tracked] ?: emptySet(),
            collectionPaused = p[K.paused] ?: false,
            liveDetection = p[K.live] ?: false,
            showInterruptionAppNames = p[K.appNames] ?: false,
            depthPreference = p[K.depth]?.let { runCatching { DepthPreference.valueOf(it) }.getOrNull() } ?: DepthPreference.AUTO,
            notificationsEnabled = p[K.notif] ?: true,
            lockScreenPrivate = p[K.lockPrivate] ?: true,
            quietStartHour = p[K.quietStart] ?: 22,
            quietEndHour = p[K.quietEnd] ?: 7,
            quietHoursEnabled = p[K.quietOn] ?: true,
            remindersEnabled = p[K.reminders] ?: true,
            cloudAiEnabled = p[K.cloudAi] ?: false,
            aiBaseUrl = p[K.aiUrl] ?: "",
            aiModel = p[K.aiModel] ?: "",
            hasSeenPaywall = p[K.seenPaywall] ?: false,
        )


    suspend fun current(): AppSettings = settings.first()

    suspend fun update(f: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val cur = read(p)
            val n = f(cur)
            p[K.onboarded] = n.onboarded
            p[K.useCase] = n.useCase.name
            p[K.tracked] = n.trackedApps
            p[K.paused] = n.collectionPaused
            p[K.live] = n.liveDetection
            p[K.appNames] = n.showInterruptionAppNames
            p[K.depth] = n.depthPreference.name
            p[K.notif] = n.notificationsEnabled
            p[K.lockPrivate] = n.lockScreenPrivate
            p[K.quietStart] = n.quietStartHour
            p[K.quietEnd] = n.quietEndHour
            p[K.quietOn] = n.quietHoursEnabled
            p[K.reminders] = n.remindersEnabled
            p[K.cloudAi] = n.cloudAiEnabled
            p[K.aiUrl] = n.aiBaseUrl
            p[K.aiModel] = n.aiModel
            p[K.seenPaywall] = n.hasSeenPaywall
        }
    }

    suspend fun segmenterState(): SegmenterState =
        store.data.first()[K.segmenter]?.let { runCatching { ContextRepository.json.decodeFromString(SegmenterState.serializer(), it) }.getOrNull() }
            ?: SegmenterState()

    suspend fun saveSegmenterState(state: SegmenterState) {
        store.edit { it[K.segmenter] = ContextRepository.json.encodeToString(SegmenterState.serializer(), state) }
    }

    suspend fun usageCursor(): Long? = store.data.first()[K.cursor]

    suspend fun setUsageCursor(at: Long) {
        store.edit { it[K.cursor] = at }
    }

    /** Stable anonymous id used as RevenueCat / OneSignal external id. Contains no personal data. */
    suspend fun localUserId(): String {
        store.data.first()[K.userId]?.let { return it }
        val id = "cb_" + java.util.UUID.randomUUID().toString().replace("-", "")
        store.edit { it[K.userId] = id }
        return id
    }

    suspend fun clearAll() {
        store.edit { it.clear() }
    }
}
