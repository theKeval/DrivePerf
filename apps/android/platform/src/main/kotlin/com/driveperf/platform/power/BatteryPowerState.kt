package com.driveperf.platform.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.driveperf.domain.capture.PowerSink
import com.driveperf.domain.capture.PowerState
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.ThermalState
import java.util.concurrent.Executor

/**
 * [PowerState] on the battery broadcast, Battery Saver and the thermal status (EC-BAT, §2.7). The core lowers the IMU
 * rate and ends trips on these; nothing here decides anything.
 */
class BatteryPowerState(context: Context, private val executor: Executor) : PowerState {
    private val appContext = context.applicationContext
    private val power = appContext.getSystemService(PowerManager::class.java)
    private var receiver: BroadcastReceiver? = null
    private var thermal: PowerManager.OnThermalStatusChangedListener? = null

    override fun current(): PowerStatus {
        val battery = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return statusOf(battery, power.isPowerSaveMode, power.currentThermalStatus)
    }

    @Synchronized
    override fun start(sink: PowerSink) {
        stop()
        val changes = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                sink.onPower(current())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        ContextCompat.registerReceiver(appContext, changes, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val listener = PowerManager.OnThermalStatusChangedListener { sink.onPower(current()) }
        power.addThermalStatusListener(executor, listener)
        receiver = changes
        thermal = listener
    }

    @Synchronized
    override fun stop() {
        receiver?.let(appContext::unregisterReceiver)
        thermal?.let(power::removeThermalStatusListener)
        receiver = null
        thermal = null
    }

    internal companion object {
        /** Maps the sticky battery intent and the power manager readings to the port's status. */
        fun statusOf(battery: Intent?, powerSave: Boolean, thermalStatus: Int): PowerStatus {
            val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
            return PowerStatus(
                batteryPct = if (level >= 0 && scale > 0) level * PERCENT / scale else null,
                isCharging = plugged != 0,
                lowPowerMode = powerSave,
                thermal = thermalOf(thermalStatus),
            )
        }

        fun thermalOf(status: Int): ThermalState = when {
            status >= PowerManager.THERMAL_STATUS_CRITICAL -> ThermalState.CRITICAL
            status >= PowerManager.THERMAL_STATUS_MODERATE -> ThermalState.SERIOUS
            status >= PowerManager.THERMAL_STATUS_LIGHT -> ThermalState.FAIR
            else -> ThermalState.NOMINAL
        }

        private const val PERCENT = 100
    }
}
