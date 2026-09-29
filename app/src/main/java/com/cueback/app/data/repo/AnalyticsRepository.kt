package com.cueback.app.data.repo

import com.cueback.app.data.db.AnalyticsEntity
import com.cueback.app.data.db.CueBackDatabase

/** Product events from the spec. The schema has no text field, so task content cannot leak into it. */
enum class Metric {
    CONTEXT_CREATED, AUTO_CONTEXT_CREATED, WARM_START_OFFERED, WARM_START_SHOWN, WARM_START_ACCEPTED,
    WARM_START_REJECTED, ARTIFACT_OPENED, MEANINGFUL_ACTION_DETECTED, REENTRY_COMPLETED, PAYWALL_VIEWED,
    TRIAL_STARTED, PURCHASE_COMPLETED, PURCHASE_FAILED, RESTORE_COMPLETED, NOTIFICATION_OPENED, INTENT_CAPTURED,
}

class AnalyticsRepository(
    private val db: CueBackDatabase,
    private val clock: () -> Long,
    private val forward: (Metric, Double?) -> Unit = { _, _ -> },
) {
    suspend fun track(metric: Metric, value: Double? = null) {
        db.analytics().insert(AnalyticsEntity(name = metric.name, at = clock(), value = value))
        forward(metric, value)
    }

    suspend fun count(metric: Metric) = db.analytics().count(metric.name)
}
