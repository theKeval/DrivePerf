package com.driveperf.app

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.driveperf.app.navigation.EnableAutoTrips
import com.driveperf.feature.capture.AttentionReason
import com.driveperf.feature.capture.CapturePermissions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Codex review on #60: the enable walk must survive activity recreation while a permission dialog or App info is open. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class EnableAutoTripsRestoreTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the walk continues after the activity is recreated, without asking again for what it tried`() {
        val tester = StateRestorationTester(compose)
        val tried = mutableListOf<AttentionReason>()
        var session: EnableAutoTrips? = null
        tester.setContent {
            session = rememberSaveable(saver = EnableAutoTrips.Saver) { EnableAutoTrips() }.apply { fix = { tried += it } }
        }
        val missing = CapturePermissions(location = true, precise = true, background = false, activity = false)
        val before = session
        compose.runOnIdle { before?.begin(missing) }
        assertEquals(listOf(AttentionReason.NoBackgroundLocation), tried)

        tester.emulateSavedInstanceStateRestore() // rotated while App info was open

        val restored = session
        assertNotSame(before, restored)
        compose.runOnIdle { restored?.continueWith(missing) } // back from Settings, still refused
        assertEquals(listOf(AttentionReason.NoBackgroundLocation, AttentionReason.NoActivityRecognition), tried)
        compose.runOnIdle { restored?.continueWith(missing) }
        assertEquals(2, tried.size)
    }

    @Test
    fun `a cancelled walk stays cancelled after recreation`() {
        val tester = StateRestorationTester(compose)
        val tried = mutableListOf<AttentionReason>()
        var session: EnableAutoTrips? = null
        tester.setContent {
            session = rememberSaveable(saver = EnableAutoTrips.Saver) { EnableAutoTrips() }.apply { fix = { tried += it } }
        }
        val missing = CapturePermissions.ALL.copy(activity = false)
        compose.runOnIdle {
            session?.begin(missing)
            session?.cancel()
        }
        tester.emulateSavedInstanceStateRestore()
        compose.runOnIdle { session?.continueWith(missing) }
        assertEquals(listOf(AttentionReason.NoActivityRecognition), tried)
    }
}
