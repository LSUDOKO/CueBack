package com.cueback.app.notify

import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.data.repo.AppSettings

enum class NotificationKind { RESUME_CANDIDATE, INTENT_PROMPT, UNRESOLVED_REMINDER }

/** Pure decision logic, so quiet hours / suppression / opt-out are unit tested. */
object NotificationPolicy {
    const val RESUME_COOLDOWN_MS = 30 * 60_000L
    const val REMINDER_MIN_AGE_MS = 24 * 60 * 60_000L

    fun inQuietHours(settings: AppSettings, hour: Int): Boolean {
        if (!settings.quietHoursEnabled) return false
        val s = settings.quietStartHour
        val e = settings.quietEndHour
        return if (s == e) false else if (s < e) hour in s until e else hour >= s || hour < e
    }

    fun allowed(
        kind: NotificationKind,
        settings: AppSettings,
        ctx: ContextCapsule?,
        hour: Int,
        now: Long,
        lastNotifiedAt: Long?,
        remindersAllowedByPlan: Boolean,
    ): Boolean {
        if (!settings.notificationsEnabled) return false
        if (ctx?.muted == true) return false
        if (inQuietHours(settings, hour)) return false
        return when (kind) {
            NotificationKind.RESUME_CANDIDATE, NotificationKind.INTENT_PROMPT ->
                lastNotifiedAt == null || now - lastNotifiedAt >= RESUME_COOLDOWN_MS
            NotificationKind.UNRESOLVED_REMINDER -> {
                ctx != null && settings.remindersEnabled && remindersAllowedByPlan &&
                    ctx.status == ContextStatus.PAUSED &&
                    ctx.nextAction != null &&
                    (ctx.pausedAt ?: now) <= now - REMINDER_MIN_AGE_MS &&
                    // one useful reminder per pause; ignored reminders are never repeated
                    lastNotifiedAt == null
            }
        }
    }

    /** Lock-screen-safe text never includes task content. */
    fun publicText(kind: NotificationKind) = when (kind) {
        NotificationKind.RESUME_CANDIDATE -> "Your next action is ready."
        NotificationKind.INTENT_PROMPT -> "CueBack saved your place."
        NotificationKind.UNRESOLVED_REMINDER -> "A saved context is waiting."
    }
}
