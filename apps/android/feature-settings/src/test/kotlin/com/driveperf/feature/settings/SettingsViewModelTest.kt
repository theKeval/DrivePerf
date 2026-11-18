package com.driveperf.feature.settings

import com.driveperf.domain.settings.Appearance
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.SettingsRepository
import com.driveperf.domain.settings.SettingsVehicle
import com.driveperf.domain.settings.UnitSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun before() = Dispatchers.setMain(dispatcher)
    @After fun after() = Dispatchers.resetMain()

    @Test fun quickChangesPreserveEachOtherAndRecreationReadsSavedChoices() = runTest(dispatcher) {
        val repository = FakeSettings()
        val model = SettingsViewModel(repository)
        model.change { it.copy(units = UnitSystem.IMPERIAL) }
        model.change { it.copy(appearance = Appearance.LIGHT) }
        advanceUntilIdle()
        assertEquals(UnitSystem.IMPERIAL, model.state.value.units)
        assertEquals(Appearance.LIGHT, model.state.value.appearance)
        assertEquals(model.state.value, SettingsViewModel(repository).state.value)
        assertEquals(listOf("car"), model.cars.value.map { it.id })
        repository.succeeds = false
        model.change { it.copy(batterySaverRecording = true) }
        advanceUntilIdle()
        assertEquals(true, model.saveFailed.value)
        assertEquals(false, model.state.value.batterySaverRecording)
        model.dismissError()
        assertEquals(false, model.saveFailed.value)
    }

    private class FakeSettings : SettingsRepository {
        override val state = MutableStateFlow(Settings())
        var succeeds = true
        override suspend fun update(settings: Settings): Boolean {
            delay(1)
            if (succeeds) state.value = settings
            return succeeds
        }
        override suspend fun vehicles() = listOf(SettingsVehicle("car", "My car"))
    }
}
