package com.cueback.app.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import com.cueback.app.MainActivity
import com.cueback.app.R
import com.cueback.app.core.model.ContextCapsule

class Notifier(private val context: Context) {
    private val nm = NotificationManagerCompat.from(context)

    fun createChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CH_RESUME, "Resume suggestions", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When CueBack recognizes you are back in a paused task"
                },
                NotificationChannel(CH_INTENT, "Save your place", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Asks what you were about to do next when CueBack could not tell"
                },
                NotificationChannel(CH_REMINDER, "Unresolved context reminders", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "At most one reminder per paused context"
                },
                NotificationChannel(CH_SERVICE, "Live detection", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Shown while live detection is running"
                    setShowBadge(false)
                },
            ),
        )
    }

    fun canPost(): Boolean {
        val granted = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && nm.areNotificationsEnabled()
    }

    fun resumeCandidate(ctx: ContextCapsule, appLabel: String?, private: Boolean) {
        val title = appLabel?.let { "You're back in $it" } ?: "Welcome back"
        val body = ctx.nextAction?.let { "Next: ${it.text}" } ?: "You were on “${ctx.title.text}”."
        // The return supersedes any pending "what's next?" prompt or reminder for the same context.
        nm.cancel(ctx.id.hashCode() + INTENT_OFFSET)
        nm.cancel(ctx.id.hashCode() + REMINDER_OFFSET)
        post(
            id = ctx.id.hashCode(),
            channel = CH_RESUME,
            title = title,
            text = body,
            deepLink = DeepLinks.warmStart(ctx.id, "notification"),
            kind = NotificationKind.RESUME_CANDIDATE,
            private = private,
            actions = listOf(NotificationCompat.Action(0, "Resume", openIntent(DeepLinks.warmStart(ctx.id, "notification"), ctx.id.hashCode() + 1))),
        )
    }

    /** "What were you about to do next?" with an inline reply — answering takes a few seconds. */
    fun intentPrompt(ctx: ContextCapsule, private: Boolean) {
        val reply = RemoteInput.Builder(KEY_REPLY).setLabel("What were you about to do next?").build()
        val replyIntent = PendingIntent.getBroadcast(
            context,
            ctx.id.hashCode() + 2,
            Intent(context, IntentReplyReceiver::class.java).putExtra(EXTRA_CONTEXT_ID, ctx.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val action = NotificationCompat.Action.Builder(0, "Add next step", replyIntent).addRemoteInput(reply).build()
        post(
            id = ctx.id.hashCode() + INTENT_OFFSET,
            channel = CH_INTENT,
            title = "Saved your place",
            text = "“${ctx.title.text}” — what were you about to do next?",
            deepLink = DeepLinks.context(ctx.id),
            kind = NotificationKind.INTENT_PROMPT,
            private = private,
            actions = listOf(action),
        )
    }

    fun reminder(ctx: ContextCapsule, private: Boolean) {
        post(
            id = ctx.id.hashCode() + REMINDER_OFFSET,
            channel = CH_REMINDER,
            title = "CueBack saved your place",
            text = "“${ctx.title.text}” is still waiting at: ${ctx.nextAction?.text ?: "your last step"}",
            deepLink = DeepLinks.warmStart(ctx.id, "reminder"),
            kind = NotificationKind.UNRESOLVED_REMINDER,
            private = private,
        )
    }

    fun cancelFor(contextId: String) {
        nm.cancel(contextId.hashCode())
        nm.cancel(contextId.hashCode() + INTENT_OFFSET)
        nm.cancel(contextId.hashCode() + REMINDER_OFFSET)
    }

    fun cancelAll() = nm.cancelAll()

    fun serviceNotification(trackedCount: Int): Notification =
        NotificationCompat.Builder(context, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat_cueback)
            .setContentTitle("CueBack is keeping your place")
            .setContentText("Watching $trackedCount selected app${if (trackedCount == 1) "" else "s"} · no screen or audio recording")
            .setContentIntent(openIntent("${DeepLinks.SCHEME}://home", 7))
            .setOngoing(true)
            .setGroup(GROUP_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

    private fun post(
        id: Int,
        channel: String,
        title: String,
        text: String,
        deepLink: String,
        kind: NotificationKind,
        private: Boolean,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        if (!canPost()) return
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_cueback)
            .setContentTitle("CueBack")
            .setContentText(NotificationPolicy.publicText(kind))
            .build()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_cueback)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openIntent(deepLink, id))
            // An explicit group per notification stops Android auto-bundling them behind a summary whose tap only opens the app.
            .setGroup("cueback_$id")
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(if (private) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setPublicVersion(public)
        actions.forEach(builder::addAction)
        try {
            nm.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between check and post; nothing to do.
        }
    }

    private fun openIntent(deepLink: String, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(Intent.ACTION_VIEW, Uri.parse(deepLink), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val GROUP_SERVICE = "cueback_service"
        const val CH_RESUME = "resume"
        const val CH_INTENT = "intent"
        const val CH_REMINDER = "reminder"
        const val CH_SERVICE = "live_detection"
        const val KEY_REPLY = "next_action"
        const val EXTRA_CONTEXT_ID = "context_id"
        const val SERVICE_ID = 4242
        private const val INTENT_OFFSET = 11
        private const val REMINDER_OFFSET = 22
    }
}
