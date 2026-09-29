package com.cueback.app.data.repo

import com.cueback.app.core.engine.Keywords
import com.cueback.app.core.model.ContextCapsule
import com.cueback.app.core.model.ContextStatus
import com.cueback.app.core.model.Fact
import com.cueback.app.core.model.UseCase
import com.cueback.app.data.db.ArtifactEntity
import com.cueback.app.data.db.CueBackDatabase
import com.cueback.app.data.db.ContextEntity
import com.cueback.app.data.db.TimelineEntity
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

enum class TimelineKind { CREATED, EVOLVED, WARM_START, RESUMED, INTENT, COMPLETED, REOPENED, EDITED }

enum class TimeWindow { ALL, TODAY, YESTERDAY, WEEK }

data class LibraryFilter(
    val query: String = "",
    val window: TimeWindow = TimeWindow.ALL,
    val useCase: UseCase? = null,
    val unresolvedOnly: Boolean = false,
    val completedOnly: Boolean = false,
)

class ContextRepository(
    private val db: CueBackDatabase,
    private val clock: () -> Long,
    private val zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
) {
    private val dao = db.contexts()

    fun observeOpen(): Flow<List<ContextCapsule>> = dao.observeOpen().map { list -> list.map(::decode) }

    fun observe(id: String): Flow<ContextCapsule?> = dao.observe(id).map { it?.let(::decode) }

    fun observeOpenCount(): Flow<Int> = dao.observeOpenCount()

    fun search(filter: LibraryFilter): Flow<List<ContextCapsule>> {
        val (since, until) = window(filter.window)
        val status = when {
            filter.completedOnly -> ContextStatus.COMPLETED.name
            filter.unresolvedOnly -> "OPEN"
            else -> null
        }
        return dao.search(filter.query.trim().lowercase(), status, filter.useCase?.name, since, until)
            .map { list -> list.map(::decode) }
    }

    suspend fun get(id: String): ContextCapsule? = dao.get(id)?.let(::decode)

    suspend fun open(): List<ContextCapsule> = dao.open().map(::decode)

    suspend fun openCount(): Int = dao.openCount()

    suspend fun all(): List<ContextCapsule> = dao.all().map(::decode)

    suspend fun save(capsule: ContextCapsule, kind: TimelineKind, summary: String) {
        db.withTransaction {
            dao.upsert(encode(capsule))
            db.artifacts().replace(
                capsule.id,
                capsule.artifacts.map {
                    ArtifactEntity(contextId = capsule.id, type = it.type.name, locator = it.locator, title = it.title, sourcePackage = it.sourcePackage, capturedAt = it.capturedAt)
                },
            )
            db.timeline().insert(TimelineEntity(contextId = capsule.id, at = clock(), kind = kind.name, summary = summary))
        }
    }

    suspend fun update(id: String, kind: TimelineKind, summary: String, change: (ContextCapsule) -> ContextCapsule): ContextCapsule? {
        val current = get(id) ?: return null
        val next = change(current)
        save(next, kind, summary)
        return next
    }

    suspend fun setStatus(id: String, status: ContextStatus) = update(
        id,
        when (status) {
            ContextStatus.COMPLETED -> TimelineKind.COMPLETED
            ContextStatus.ACTIVE -> TimelineKind.RESUMED
            else -> TimelineKind.REOPENED
        },
        "Marked ${status.name.lowercase()}",
    ) {
        it.copy(status = status, resumedAt = if (status == ContextStatus.ACTIVE) clock() else it.resumedAt, pausedAt = if (status == ContextStatus.PAUSED) clock() else it.pausedAt)
    }

    /** Human intent capture: the answer to "What were you about to do next?". */
    suspend fun setNextAction(id: String, text: String) = update(id, TimelineKind.INTENT, "Next action set") {
        it.copy(nextAction = Fact.user(text.trim()), confidence = maxOf(it.confidence, 0.85))
    }

    suspend fun setMuted(id: String, muted: Boolean) = update(id, TimelineKind.EDITED, if (muted) "Reminders muted" else "Reminders on") {
        it.copy(muted = muted)
    }

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun deleteDemo() = dao.deleteDemo()

    fun observeTimeline(id: String) = db.timeline().observe(id)

    fun observeArtifacts() = db.artifacts().observeAll()

    private fun window(w: TimeWindow): Pair<Long, Long> {
        val now = clock()
        val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        return when (w) {
            TimeWindow.ALL -> 0L to Long.MAX_VALUE
            TimeWindow.TODAY -> today to Long.MAX_VALUE
            TimeWindow.YESTERDAY -> (today - DAY) to today
            TimeWindow.WEEK -> (today - 6 * DAY) to Long.MAX_VALUE
        }
    }

    companion object {
        private const val DAY = 24 * 60 * 60_000L
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(c: ContextCapsule) = ContextEntity(
            id = c.id,
            title = c.title.text,
            nextAction = c.nextAction?.text,
            status = c.status.name,
            primaryApp = c.primaryApp,
            useCase = c.useCase.name,
            confidence = c.confidence,
            createdAt = c.createdAt,
            pausedAt = c.pausedAt,
            resumedAt = c.resumedAt,
            isDemo = c.isDemo,
            muted = c.muted,
            searchText = searchText(c),
            json = json.encodeToString(ContextCapsule.serializer(), c),
        )

        fun decode(e: ContextEntity): ContextCapsule = json.decodeFromString(ContextCapsule.serializer(), e.json)

        fun searchText(c: ContextCapsule): String = buildList {
            add(c.title.text); c.goal?.let { add(it.text) }; c.nextAction?.let { add(it.text) }; c.blocker?.let { add(it.text) }
            c.completed.forEach { add(it.text) }; c.notes.forEach { add(it.text) }
            c.artifacts.forEach { a -> a.title?.let(::add); add(a.locator); Keywords.domainOf(a.locator)?.let(::add) }
            c.primaryApp?.let(::add); addAll(c.keywords)
        }.joinToString(" ").lowercase()
    }
}
