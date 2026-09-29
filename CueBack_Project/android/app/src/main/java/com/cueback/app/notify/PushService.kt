package com.cueback.app.notify

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.cueback.app.BuildConfig
import com.cueback.app.MainActivity
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.onesignal.notifications.INotificationClickEvent
import com.onesignal.notifications.INotificationClickListener

/**
 * OneSignal integration. Push is used for re-engagement Journeys configured in the OneSignal
 * dashboard using the privacy-safe tags set here (counts and timestamps only, never task content).
 */
class PushService(private val context: Context) {
    val configured: Boolean get() = BuildConfig.ONESIGNAL_APP_ID.isNotBlank()
    private var initialized = false

    fun init(externalId: String) {
        if (!configured || initialized) return
        if (BuildConfig.DEBUG) OneSignal.Debug.logLevel = LogLevel.WARN
        try {
            OneSignal.initWithContext(context, BuildConfig.ONESIGNAL_APP_ID)
            OneSignal.login(externalId)
        } catch (e: RuntimeException) {
            return // local notifications still work without push
        }
        OneSignal.Notifications.addClickListener(object : INotificationClickListener {
            override fun onClick(event: INotificationClickEvent) {
                val contextId = event.notification.additionalData?.optString("context_id")?.takeIf { it.isNotBlank() }
                val link = DeepLinks.fromPush(contextId, event.result.url ?: event.notification.launchURL)
                val uri = when (link) {
                    is DeepLink.WarmStart -> DeepLinks.warmStart(link.contextId, "push")
                    is DeepLink.ContextDetail -> DeepLinks.context(link.contextId)
                    DeepLink.Library -> "${DeepLinks.SCHEME}://library"
                    DeepLink.Capture -> "${DeepLinks.SCHEME}://capture"
                    DeepLink.Home, null -> "${DeepLinks.SCHEME}://home"
                }
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                OneSignal.Session.addOutcome("notification_opened")
            }
        })
        initialized = true
    }

    /** Returns true if permission is granted. Falls back to the OS prompt handled by the caller. */
    suspend fun requestPermission(): Boolean =
        if (initialized) OneSignal.Notifications.requestPermission(true) else false

    fun syncTags(openContexts: Int, unresolvedWithNext: Int, lastPauseAtSec: Long?, useCase: String, pro: Boolean) {
        if (!initialized) return
        OneSignal.User.addTags(
            buildMap {
                put("open_contexts", openContexts.toString())
                put("unresolved_with_next", unresolvedWithNext.toString())
                put("use_case", useCase.lowercase())
                put("pro", if (pro) "1" else "0")
                lastPauseAtSec?.let { put("last_pause_at", it.toString()) }
            },
        )
    }

    fun outcome(name: String, value: Float? = null) {
        if (!initialized) return
        if (value != null) OneSignal.Session.addOutcomeWithValue(name, value) else OneSignal.Session.addOutcome(name)
    }

    fun setOptOut(optOut: Boolean) {
        if (!initialized) return
        if (optOut) OneSignal.User.pushSubscription.optOut() else OneSignal.User.pushSubscription.optIn()
    }

    fun logoutAndClear() {
        if (!initialized) return
        OneSignal.User.removeTags(listOf("open_contexts", "unresolved_with_next", "use_case", "pro", "last_pause_at"))
        OneSignal.logout()
    }
}
