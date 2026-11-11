package com.driveperf.platform.power

import android.content.Intent
import android.os.BatteryManager
import android.os.PowerManager
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.ThermalState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BatteryPowerStateTest {
    @Test
    fun `the battery intent maps to percent and charging`() {
        val battery = Intent(Intent.ACTION_BATTERY_CHANGED)
            .putExtra(BatteryManager.EXTRA_LEVEL, 37)
            .putExtra(BatteryManager.EXTRA_SCALE, 50)
            .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        assertEquals(
            PowerStatus(74, isCharging = true, lowPowerMode = true, thermal = ThermalState.NOMINAL),
            BatteryPowerState.statusOf(battery, powerSave = true, thermalStatus = PowerManager.THERMAL_STATUS_NONE),
        )
        assertEquals(
            PowerStatus(null, isCharging = false, lowPowerMode = false, thermal = ThermalState.NOMINAL),
            BatteryPowerState.statusOf(null, powerSave = false, thermalStatus = PowerManager.THERMAL_STATUS_NONE),
        )
    }

    @Test
    fun `thermal status maps to the four shared levels`() {
        assertEquals(ThermalState.FAIR, BatteryPowerState.thermalOf(PowerManager.THERMAL_STATUS_LIGHT))
        assertEquals(ThermalState.SERIOUS, BatteryPowerState.thermalOf(PowerManager.THERMAL_STATUS_MODERATE))
        assertEquals(ThermalState.SERIOUS, BatteryPowerState.thermalOf(PowerManager.THERMAL_STATUS_SEVERE))
        assertEquals(ThermalState.CRITICAL, BatteryPowerState.thermalOf(PowerManager.THERMAL_STATUS_SHUTDOWN))
    }
}
