package com.driveperf.data.local

import com.driveperf.domain.analysis.AnalysisAxis
import com.driveperf.domain.analysis.AnalysisChannel
import com.driveperf.domain.analysis.AnalysisChoices
import com.driveperf.testing.TestClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalAnalysisPreferencesTest {
    private val driver = JvmDatabaseDriverFactory().create()
    private val db = openDatabase(driver)
    private fun prefs() = LocalAnalysisPreferences(db, Dispatchers.Unconfined, TestClock())

    @Test fun choicesSurviveRecreationAndKeepTheProfileFields() = runTest {
        assertEquals(AnalysisChoices(), prefs().read())
        val chosen = AnalysisChoices(setOf(AnalysisChannel.ELEVATION, AnalysisChannel.SPEED), AnalysisAxis.DISTANCE)
        assertTrue(prefs().save(chosen))
        assertEquals(chosen, prefs().read())
        val stored = db.analysisChannelsQueries.analysisChannels().executeAsOne()
        assertEquals("""{"channels":["speed","elevation"],"axis":"distance"}""", stored)
        assertEquals("{}", db.profileQueries.localSettings().executeAsOne().units_json)
    }

    @Test fun theColumnDefaultsAndDamagedValuesReadAsDefaults() = runTest {
        db.profileQueries.ensureLocalProfile(1, 1, 1)
        assertEquals(AnalysisChoices(), prefs().read())
        db.analysisChannelsQueries.saveAnalysisChannels("""["jerk","gone"]""", 2)
        assertEquals(AnalysisChoices(setOf(AnalysisChannel.JERK)), prefs().read())
        db.analysisChannelsQueries.saveAnalysisChannels("""{"channels":["gone"],"axis":"sideways"}""", 3)
        assertEquals(AnalysisChoices(), prefs().read())
        db.analysisChannelsQueries.saveAnalysisChannels("not json", 4)
        assertEquals(AnalysisChoices(), prefs().read())
    }

    @Test fun aFailedWriteIsReported() = runTest {
        driver.close()
        assertFalse(prefs().save(AnalysisChoices()))
    }
}
