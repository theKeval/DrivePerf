package com.driveperf.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.driveperf.domain.model.DistanceUnit
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import org.junit.Test

class SettingsRepositoryImplTest {

    @Test
    fun observeSettings_defaultsToKilometres() = runTest {
        val file = createTempPreferencesFile()
        val (repository, scope) = createRepository(file, testScheduler)

        try {
            val settings = repository.observeSettings().first()

            assertThat(settings.autoDetectEnabled).isTrue()
            assertThat(settings.cloudSyncEnabled).isFalse()
            assertThat(settings.distanceUnit).isEqualTo(DistanceUnit.KILOMETERS)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun setDistanceUnit_persistsAcrossRepositoryInstances() = runTest {
        val file = createTempPreferencesFile()
        val (firstRepository, firstScope) = createRepository(file, testScheduler)

        try {
            firstRepository.setDistanceUnit(DistanceUnit.MILES)
            advanceUntilIdle()
        } finally {
            firstScope.cancel()
        }

        val (secondRepository, secondScope) = createRepository(file, testScheduler)
        try {
            val settings = secondRepository.observeSettings().first()

            assertThat(settings.distanceUnit).isEqualTo(DistanceUnit.MILES)
        } finally {
            secondScope.cancel()
            file.delete()
        }
    }

    @Test
    fun observeSettings_mapsLegacyKilometersValue() = runTest {
        val file = createTempPreferencesFile()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        val repository = SettingsRepositoryImpl(dataStore)

        try {
            dataStore.edit { prefs -> prefs[testDistanceUnitKey] = "KILOMETRES" }
            advanceUntilIdle()

            val settings = repository.observeSettings().first()

            assertThat(settings.distanceUnit).isEqualTo(DistanceUnit.KILOMETERS)
        } finally {
            scope.cancel()
            file.delete()
        }
    }

    @Test
    fun observeSettings_recoversFromIOExceptionWithDefaults() = runTest {
        val dataStore = mockk<DataStore<Preferences>>()
        every { dataStore.data } returns flow { throw IOException("corrupted") }

        val settings = SettingsRepositoryImpl(dataStore).observeSettings().first()

        assertThat(settings.autoDetectEnabled).isTrue()
        assertThat(settings.cloudSyncEnabled).isFalse()
        assertThat(settings.distanceUnit).isEqualTo(DistanceUnit.KILOMETERS)
    }

    @Test
    fun observeSettings_rethrowsNonIOException() = runTest {
        val dataStore = mockk<DataStore<Preferences>>()
        every { dataStore.data } returns flow { throw IllegalStateException("boom") }

        val thrown = assertFailsWith<IllegalStateException> {
            SettingsRepositoryImpl(dataStore).observeSettings().first()
        }

        assertThat(thrown).hasMessageThat().isEqualTo("boom")
    }

    private fun createRepository(
        file: File,
        scheduler: TestCoroutineScheduler,
    ): Pair<SettingsRepositoryImpl, CoroutineScope> {
        val scope = CoroutineScope(StandardTestDispatcher(scheduler) + SupervisorJob())
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope,
            produceFile = { file },
        )
        return SettingsRepositoryImpl(dataStore) to scope
    }

    private fun createTempPreferencesFile(): File =
        Files.createTempFile("settings", ".preferences_pb").toFile()

    // The production KEY_DISTANCE_UNIT is private; this duplicates its value to seed DataStore
    // for legacy migration tests.
    private val testDistanceUnitKey = stringPreferencesKey("distance_unit")
}
