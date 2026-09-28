package com.cueback.app.core.engine

/**
 * Every threshold the engine uses lives here. Values are engineering defaults to be calibrated with real
 * traces, not scientific constants.
 */
data class EngineConfig(
    // Segmentation: pause score signal weights (summed, capped at 1.0)
    val weightLocked: Double = 0.60,
    val weightUntrackedApp: Double = 0.35,
    val weightIdle: Double = 0.30,
    val weightExplicitPause: Double = 0.90,
    val finalizeThreshold: Double = 0.60,
    /** Pause score must persist this long before a session is finalized (except explicit pause). */
    val minAwayMs: Long = 30_000,
    /** Away time after which the idle signal fires. */
    val idleThresholdMs: Long = 5 * 60_000,
    /** Sessions with less tracked foreground time and no user evidence are discarded as noise. */
    val minSessionMs: Long = 60_000,
    /** Untracked focus shorter than this between tracked focuses is ignored. */
    val rapidSwitchMs: Long = 3_000,

    // Confidence weights
    val cSourceContinuity: Double = 0.25,
    val cArtifactContinuity: Double = 0.20,
    val cTemporalContinuity: Double = 0.15,
    val cActionSequence: Double = 0.15,
    val cExplicitIntent: Double = 0.15,
    val cSemantic: Double = 0.10,
    val temporalFullMs: Long = 10 * 60_000,
    /** Below this capsule confidence, CueBack asks "What were you about to do next?". */
    val askIntentBelow: Double = 0.55,

    // Return matching weights (normalized over signals present in the observation)
    val mPrimaryApp: Double = 0.40,
    val mSecondaryApp: Double = 0.15,
    val mArtifactExact: Double = 0.20,
    val mArtifactDomain: Double = 0.10,
    val mKeyword: Double = 0.10,
    val mRecency: Double = 0.30,
    val recencyHalfLifeMs: Long = 12 * 60 * 60_000,
    val ambiguityMargin: Double = 0.10,
    val ambiguityPenalty: Double = 0.25,
    val autoThreshold: Double = 0.80,
    val suggestThreshold: Double = 0.60,
    val inboxThreshold: Double = 0.40,
    /** Returns sooner than this reattach silently: no warm start for a glance at another app. */
    val silentReattachMs: Long = 2 * 60_000,

    // Recovery depth bands
    val microMaxMs: Long = 5 * 60_000,
    val fullMinMs: Long = 120 * 60_000,
    val significantDeltaCount: Int = 3,

    // Re-entry
    val meaningfulDwellMs: Long = 15_000,
    val reentryTimeoutMs: Long = 30 * 60_000,
)
