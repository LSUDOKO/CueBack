package com.cueback.app.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.cueback.app.CueBackApp
import com.cueback.app.data.repo.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Inline reply to "What were you about to do next?" — becomes the context's next action (You said). */
class IntentReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(Notifier.EXTRA_CONTEXT_ID) ?: return
        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifier.KEY_REPLY)?.toString()?.trim()
        if (text.isNullOrEmpty() || text.length > 500) return
        val container = (context.applicationContext as CueBackApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.contexts.setNextAction(id, text)
                container.analytics.track(Metric.INTENT_CAPTURED)
                NotificationManagerCompat.from(context).cancel(id.hashCode() + 11)
            } finally {
                pending.finish()
            }
        }
    }
}
