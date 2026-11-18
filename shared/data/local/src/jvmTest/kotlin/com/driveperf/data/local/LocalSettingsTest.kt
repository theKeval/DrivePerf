package com.driveperf.data.local

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.settings.Appearance
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.testing.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalSettingsTest {
    private val driver = JvmDatabaseDriverFactory().create()
    private val db = openDatabase(driver)
    private val events = mutableListOf<AnalyticsEvent>()
    private val analytics = object : Analytics {
        override fun track(event: AnalyticsEvent) { events += event }
        override fun setUsageConsent(allowed: Boolean) = Unit
        override fun resetId() = Unit
    }
    private fun repository() = LocalSettingsRepository(db, Dispatchers.Unconfined, TestClock(), analytics)

    @Test fun defaultCarComesFromGarageAndOtherProfileFieldsSurvive() = runTest {
        db.profileQueries.ensureLocalProfile(1, 1, 1)
        driver.execute(null, "UPDATE profile SET display_name = 'Local driver', capture_json = '{\"autoDetect\":true}'", 0)
        driver.execute(null, """
            INSERT INTO vehicle(id, nickname, added_at, created_at, updated_at, origin_device_id)
            VALUES ('car', 'My car', 1, 1, 1, 'test')
        """.trimIndent(), 0)
        val repo = repository()
        assertEquals("My car", repo.vehicles().single().name)
        assertTrue(repo.update(repo.state.value.copy(defaultVehicleId = "car")))
        assertEquals("car", repository().state.value.defaultVehicleId)
        assertTrue(db.profileQueries.localSettings().executeAsOne().capture_json.contains("\"autoDetect\":true"))
        assertTrue(repo.update(Settings()))
        assertEquals(null, repository().state.value.defaultVehicleId)
        assertEquals(listOf("default_car", "default_car"), events.map { it.properties.getValue("key") })
    }

    @Test fun choicesSurviveRecreationAndOnlyChangesEmitKeys() = runTest {
        val repo = repository()
        assertEquals(Settings(), repo.state.value)
        assertTrue(repo.vehicles().isEmpty())
        val selected = Settings(UnitSystem.IMPERIAL, Appearance.DARK, batterySaverRecording = true, dynamicColour = true)
        assertTrue(repo.update(selected))
        assertEquals(selected, repo.state.value)
        assertEquals(selected, repository().state.value)
        assertEquals(setOf("units", "appearance", "battery_saver_recording", "dynamic_colour"),
            events.map { it.properties.getValue("key") }.toSet())
        assertTrue(repo.update(selected))
        assertEquals(4, events.size)
        assertFalse(repo.update(selected.copy(defaultVehicleId = "missing")))
        assertEquals(selected, repo.state.value)
        assertTrue(repo.update(Settings()))
        assertEquals(Settings(), repository().state.value)
    }
}
