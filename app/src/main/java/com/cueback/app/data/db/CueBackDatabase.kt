package com.cueback.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ContextEntity::class,
        ArtifactEntity::class,
        TimelineEntity::class,
        EventEntity::class,
        ReentryEntity::class,
        AnalyticsEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class CueBackDatabase : RoomDatabase() {
    abstract fun contexts(): ContextDao
    abstract fun artifacts(): ArtifactDao
    abstract fun timeline(): TimelineDao
    abstract fun events(): EventDao
    abstract fun reentry(): ReentryDao
    abstract fun analytics(): AnalyticsDao

    companion object {
        const val NAME = "cueback.db"

        /** Future schema changes add explicit Migration objects here; destructive fallback is never used. */
        val MIGRATIONS = emptyArray<androidx.room.migration.Migration>()

        fun build(context: Context): CueBackDatabase =
            Room.databaseBuilder(context, CueBackDatabase::class.java, NAME)
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
