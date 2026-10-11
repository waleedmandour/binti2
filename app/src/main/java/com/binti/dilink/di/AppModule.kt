// SPDX-License-Identifier: Apache-2.0
// Copyright 2024-2025 Dr. Waleed Mandour

package com.binti.dilink.di

import android.content.Context
import android.content.SharedPreferences
import com.binti.dilink.data.local.BintiDatabase
import com.binti.dilink.data.local.dao.StationDao
import com.binti.dilink.data.repository.StationRepository
import com.binti.dilink.dilink.VehicleDataRepository
import com.binti.dilink.utils.StationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt application-level module — provides app-wide singletons.
 *
 * Phase 2: sets up the DI graph for new code. Existing singletons
 * (VehicleDataRepository, StationManager) are provided here so new
 * @Inject-annotated classes can receive them. The existing classes
 * continue to work via their getInstance() pattern — no migration
 * of existing code is needed.
 *
 * @author Dr. Waleed Mandour
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Provides the main SharedPreferences ("binti_prefs") used for
     * user settings, model download state, and general app config.
     */
    @Provides
    @Singleton
    fun provideBintiPrefs(@ApplicationContext context: Context): SharedPreferences =
        context.getSharedPreferences("binti_prefs", Context.MODE_PRIVATE)

    /**
     * Provides the Room database instance. This is the single source of
     * truth for structured data (charging stations, diagnostics events).
     * Existing asset-based data (stations.json) is migrated into Room on
     * first access via [StationRepository].
     */
    @Provides
    @Singleton
    fun provideBintiDatabase(@ApplicationContext context: Context): BintiDatabase =
        BintiDatabase.getInstance(context)

    /**
     * Provides the StationDao for charging station queries.
     */
    @Provides
    @Singleton
    fun provideStationDao(db: BintiDatabase): StationDao = db.stationDao()

    /**
     * Provides the StationRepository — abstracts the data source (Room DB
     * with asset fallback) for charging station queries. New code should
     * use this instead of [StationManager] directly.
     */
    @Provides
    @Singleton
    fun provideStationRepository(
        stationDao: StationDao,
        @ApplicationContext context: Context
    ): StationRepository = StationRepository(stationDao, context)

    /**
     * Provides the [VehicleDataRepository] singleton.
     *
     * Phase 2: the existing [DiLinkAccessibilityService] creates its own
     * instance (`val vehicleDataRepository = VehicleDataRepository()`).
     * Phase 3 will refactor the service to receive this Hilt-provided
     * instance via @Inject. For now, this provider creates a shared
     * singleton that new @Inject-annotated classes can receive — the
     * existing service instance continues to work independently.
     *
     * When the service is migrated to Hilt, it will receive this same
     * singleton, unifying the two instances.
     */
    @Provides
    @Singleton
    fun provideVehicleDataRepository(): VehicleDataRepository =
        VehicleDataRepository()

    /**
     * Provides the existing [StationManager] for legacy code paths.
     * New code should prefer [StationRepository] (Room-backed).
     * This provider exists so the existing [DiLinkCommandExecutor]
     * can be migrated to @Inject without breaking its StationManager dep.
     */
    @Provides
    @Singleton
    fun provideStationManager(@ApplicationContext context: Context): StationManager =
        StationManager(context)
}
