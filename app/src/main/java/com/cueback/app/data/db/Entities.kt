package com.cueback.app.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Queryable columns are denormalized; the full capsule (facts with provenance) is stored as JSON. */
@Entity(
    tableName = "contexts",
    indices = [Index("status"), Index("pausedAt"), Index("primaryApp")],
)
data class ContextEntity(
    @PrimaryKey val id: String,
    val title: String,
    val nextAction: String?,
    val status: String,
    val primaryApp: String?,
    val useCase: String,
    val confidence: Double,
    val createdAt: Long,
    val pausedAt: Long?,
    val resumedAt: Long?,
    val isDemo: Boolean,
    val muted: Boolean,
    /** Lower-cased title, notes, artifact titles/locators and keywords for offline search. */
    val searchText: String,
    val json: String,
)

@Entity(
    tableName = "context_artifacts",
    foreignKeys = [ForeignKey(ContextEntity::class, ["id"], ["contextId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("contextId")],
)
data class ArtifactEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val contextId: String,
    val type: String,
    val locator: String,
    val title: String?,
    val sourcePackage: String?,
    val capturedAt: Long,
)

@Entity(
    tableName = "context_timeline",
    foreignKeys = [ForeignKey(ContextEntity::class, ["id"], ["contextId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("contextId")],
)
data class TimelineEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val contextId: String,
    val at: Long,
    val kind: String,
    val summary: String,
)

/** Structured events (never raw content), kept for a short retention window. */
@Entity(tableName = "events", indices = [Index("timestamp")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val timestamp: Long,
    val type: String,
    val packageName: String?,
    val json: String,
)

@Entity(
    tableName = "reentry_sessions",
    foreignKeys = [ForeignKey(ContextEntity::class, ["id"], ["contextId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("contextId"), Index("warmStartAt")],
)
data class ReentryEntity(
    @PrimaryKey val id: String,
    val contextId: String,
    val warmStartAt: Long,
    val acceptedAt: Long?,
    val firstMeaningfulActionAt: Long?,
    val level: String,
    val source: String,
    val outcome: String,
)

/** Privacy-safe local analytics: ids, enums and numbers only — never task content. */
@Entity(tableName = "analytics", indices = [Index("name")])
data class AnalyticsEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val name: String,
    val at: Long,
    val value: Double?,
)
