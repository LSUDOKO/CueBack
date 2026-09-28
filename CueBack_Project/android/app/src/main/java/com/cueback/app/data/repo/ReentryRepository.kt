package com.cueback.app.data.repo

import com.cueback.app.core.model.RecoveryLevel
import com.cueback.app.core.model.ReentrySession
import com.cueback.app.data.db.CueBackDatabase
import com.cueback.app.data.db.ReentryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class WarmStartSource { AUTO, NOTIFICATION, HOME, LIBRARY, DEMO }

enum class ReentryOutcome { SHOWN, ACCEPTED, REJECTED, COMPLETED, EXPIRED }

data class ReentryRecord(val session: ReentrySession, val source: WarmStartSource, val outcome: ReentryOutcome)

class ReentryRepository(private val db: CueBackDatabase, private val newId: () -> String) {
    private val dao = db.reentry()

    suspend fun start(contextId: String, at: Long, level: RecoveryLevel, source: WarmStartSource): ReentrySession {
        val s = ReentrySession(newId(), contextId, warmStartAt = at, level = level)
        dao.upsert(s.toEntity(source, ReentryOutcome.SHOWN))
        return s
    }

    suspend fun accept(id: String, at: Long) = mutate(id) { e -> e.copy(acceptedAt = at, outcome = ReentryOutcome.ACCEPTED.name) }

    suspend fun reject(id: String) = mutate(id) { e -> e.copy(outcome = ReentryOutcome.REJECTED.name) }

    suspend fun expire(id: String) = mutate(id) { e -> e.copy(outcome = ReentryOutcome.EXPIRED.name) }

    suspend fun complete(id: String, meaningfulAt: Long) = mutate(id) { e ->
        e.copy(firstMeaningfulActionAt = maxOf(meaningfulAt, e.warmStartAt), outcome = ReentryOutcome.COMPLETED.name)
    }

    suspend fun get(id: String): ReentryRecord? = dao.get(id)?.toRecord()

    /** Sessions still waiting for a meaningful action. */
    suspend fun pending(): List<ReentryRecord> = dao.pending().map { it.toRecord() }

    fun observeFor(contextId: String): Flow<List<ReentryRecord>> = dao.observeFor(contextId).map { l -> l.map { it.toRecord() } }

    fun observeRecentCompleted(limit: Int = 20): Flow<List<ReentryRecord>> = dao.observeCompleted(limit).map { l -> l.map { it.toRecord() } }

    suspend fun all() = dao.all().map { it.toRecord() }

    private suspend fun mutate(id: String, f: (ReentryEntity) -> ReentryEntity): ReentryRecord? {
        val e = dao.get(id) ?: return null
        val n = f(e)
        dao.upsert(n)
        return n.toRecord()
    }

    private fun ReentrySession.toEntity(source: WarmStartSource, outcome: ReentryOutcome) = ReentryEntity(
        id, contextId, warmStartAt, acceptedAt, firstMeaningfulActionAt, level.name, source.name, outcome.name,
    )

    private fun ReentryEntity.toRecord() = ReentryRecord(
        ReentrySession(id, contextId, warmStartAt, acceptedAt, firstMeaningfulActionAt, RecoveryLevel.valueOf(level)),
        WarmStartSource.valueOf(source),
        ReentryOutcome.valueOf(outcome),
    )
}
