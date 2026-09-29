package com.cueback.app.billing

import com.cueback.app.core.model.RecoveryLevel

const val ENTITLEMENT_PRO = "cueback_pro"

/** Entitlement state comes only from RevenueCat CustomerInfo; nothing in the app can set Pro locally. */
sealed interface EntitlementState {
    /** No RevenueCat key in this build. Billing UI explains; Pro is not available. */
    data object NotConfigured : EntitlementState
    data object Loading : EntitlementState
    data object Free : EntitlementState
    data class Pro(val isTrial: Boolean, val expiresAt: Long?, val willRenew: Boolean) : EntitlementState
    /** Could not reach RevenueCat and there is no cached CustomerInfo. Treated as Free. */
    data class Error(val message: String) : EntitlementState
}

val EntitlementState.isPro: Boolean get() = this is EntitlementState.Pro

enum class ProFeature { UNLIMITED_CONTEXTS, UNLIMITED_TRACKED_APPS, VOICE_CAPTURE, FULL_RECONSTRUCTION, CONTEXT_TIMELINE, SMART_REMINDERS }

class FeatureGate(private val state: EntitlementState) {
    val pro get() = state.isPro

    fun allows(feature: ProFeature): Boolean = pro

    val maxOpenContexts: Int get() = if (pro) Int.MAX_VALUE else FREE_OPEN_CONTEXTS
    val maxTrackedApps: Int get() = if (pro) Int.MAX_VALUE else FREE_TRACKED_APPS

    fun canCreateContext(openCount: Int) = openCount < maxOpenContexts

    /** Free users see micro + recovery card; the full reconstruction is Pro. */
    fun cap(level: RecoveryLevel): RecoveryLevel =
        if (!pro && level == RecoveryLevel.FULL) RecoveryLevel.CARD else level

    /** Tracked apps actually observed: the free tier observes the first app only. */
    fun effectiveTracked(selected: Set<String>): Set<String> =
        if (pro) selected else selected.sorted().take(FREE_TRACKED_APPS).toSet()

    companion object {
        const val FREE_OPEN_CONTEXTS = 3
        const val FREE_TRACKED_APPS = 1
    }
}
