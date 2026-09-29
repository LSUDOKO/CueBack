package com.cueback.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ContextDao {
    @Upsert
    suspend fun upsert(entity: ContextEntity)

    @Query("SELECT * FROM contexts WHERE id = :id")
    suspend fun get(id: String): ContextEntity?

    @Query("SELECT * FROM contexts WHERE id = :id")
    fun observe(id: String): Flow<ContextEntity?>

    @Query("SELECT * FROM contexts WHERE status IN ('ACTIVE','PAUSED') ORDER BY COALESCE(pausedAt, createdAt) DESC")
    fun observeOpen(): Flow<List<ContextEntity>>

    @Query("SELECT * FROM contexts WHERE status IN ('ACTIVE','PAUSED') ORDER BY COALESCE(pausedAt, createdAt) DESC")
    suspend fun open(): List<ContextEntity>

    @Query(
        """SELECT * FROM contexts
           WHERE (:query = '' OR searchText LIKE '%' || :query || '%')
             AND (:status IS NULL OR status = :status OR (:status = 'OPEN' AND status IN ('ACTIVE','PAUSED')))
             AND (:useCase IS NULL OR useCase = :useCase)
             AND COALESCE(pausedAt, createdAt) >= :since
             AND COALESCE(pausedAt, createdAt) < :until
           ORDER BY COALESCE(pausedAt, createdAt) DESC""",
    )
    fun search(query: String, status: String?, useCase: String?, since: Long, until: Long): Flow<List<ContextEntity>>

    @Query("SELECT COUNT(*) FROM contexts WHERE status IN ('ACTIVE','PAUSED') AND isDemo = 0")
    suspend fun openCount(): Int

    @Query("SELECT COUNT(*) FROM contexts WHERE status IN ('ACTIVE','PAUSED') AND isDemo = 0")
    fun observeOpenCount(): Flow<Int>

    @Query("SELECT * FROM contexts ORDER BY createdAt")
    suspend fun all(): List<ContextEntity>

    @Query("DELETE FROM contexts WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM contexts WHERE isDemo = 1")
    suspend fun deleteDemo()
}

@Dao
interface ArtifactDao {
    @Query("DELETE FROM context_artifacts WHERE contextId = :contextId")
    suspend fun clear(contextId: String)

    @Insert
    suspend fun insertAll(items: List<ArtifactEntity>)

    @Transaction
    suspend fun replace(contextId: String, items: List<ArtifactEntity>) {
        clear(contextId)
        insertAll(items)
    }

    @Query("SELECT * FROM context_artifacts ORDER BY capturedAt DESC")
    fun observeAll(): Flow<List<ArtifactEntity>>
}

@Dao
interface TimelineDao {
    @Insert
    suspend fun insert(item: TimelineEntity)

    @Query("SELECT * FROM context_timeline WHERE contextId = :contextId ORDER BY at DESC")
    fun observe(contextId: String): Flow<List<TimelineEntity>>
}

@Dao
interface EventDao {
    @Insert
    suspend fun insertAll(items: List<EventEntity>)

    @Query("DELETE FROM events WHERE timestamp < :before")
    suspend fun prune(before: Long)

    @Query("SELECT COUNT(*) FROM events")
    suspend fun count(): Int

    @Query("SELECT * FROM events ORDER BY timestamp")
    suspend fun all(): List<EventEntity>
}

@Dao
interface ReentryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReentryEntity)

    @Query("SELECT * FROM reentry_sessions WHERE id = :id")
    suspend fun get(id: String): ReentryEntity?

    @Query("SELECT * FROM reentry_sessions WHERE outcome IN ('SHOWN','ACCEPTED') ORDER BY warmStartAt DESC")
    suspend fun pending(): List<ReentryEntity>

    @Query("SELECT * FROM reentry_sessions WHERE contextId = :contextId ORDER BY warmStartAt DESC")
    fun observeFor(contextId: String): Flow<List<ReentryEntity>>

    @Query("SELECT * FROM reentry_sessions WHERE outcome = 'COMPLETED' ORDER BY warmStartAt DESC LIMIT :limit")
    fun observeCompleted(limit: Int): Flow<List<ReentryEntity>>

    @Query("SELECT * FROM reentry_sessions ORDER BY warmStartAt")
    suspend fun all(): List<ReentryEntity>
}

@Dao
interface AnalyticsDao {
    @Insert
    suspend fun insert(item: AnalyticsEntity)

    @Query("SELECT COUNT(*) FROM analytics WHERE name = :name")
    suspend fun count(name: String): Int

    @Query("SELECT * FROM analytics ORDER BY at")
    suspend fun all(): List<AnalyticsEntity>
}
