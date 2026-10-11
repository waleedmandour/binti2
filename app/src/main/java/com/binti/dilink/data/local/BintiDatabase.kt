// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.binti.dilink.data.local.dao.StationDao
import com.binti.dilink.data.local.entity.StationEntity

/**
 * Binti Room database.
 *
 * Phase 2: the database is created but not yet wired into the runtime
 * data path. The existing [com.binti.dilink.utils.StationManager] (asset-based)
 * continues to serve the voice pipeline. [StationRepository] wraps this
 * database and will gradually replace StationManager in subsequent phases.
 *
 * **Entities:**
 * - [StationEntity] — EV charging stations (migrated from `stations.json`)
 *
 * Future entities (Phase 3+):
 * - DiagnosticsEventEntity — replaces the in-memory DiagnosticJournal
 * - VehicleSnapshotEntity — optional persistent vehicle state history
 *
 * **Schema versioning:** Schema JSON is exported to `app/schemas/` for
 * migration testing (configured in `app/build.gradle.kts` ksp args).
 *
 * @author Dr. Waleed Mandour
 */
@Database(
    entities = [StationEntity::class],
    version = 1,
    exportSchema = true
)
abstract class BintiDatabase : RoomDatabase() {

    abstract fun stationDao(): StationDao

    companion object {
        @Volatile
        private var INSTANCE: BintiDatabase? = null

        /**
         * Get the singleton database instance. Uses the fallbackToDestructMigration
         * strategy for Phase 2 (v1 schema — no migrations needed yet). Phase 3
         * will add proper Migration objects when the schema evolves.
         */
        fun getInstance(context: Context): BintiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BintiDatabase::class.java,
                    "binti.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
