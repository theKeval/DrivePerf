package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.RearmReason
import com.driveperf.domain.capture.RecorderState
import com.driveperf.domain.capture.ThermalState
import com.driveperf.domain.capture.WakeEvent
import com.driveperf.domain.capture.WakeKind
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.header
import com.driveperf.pipeline.capture.CaptureFixtures.motion
import com.driveperf.pipeline.capture.CaptureFixtures.ns
import com.driveperf.pipeline.capture.CaptureFixtures.parking
import com.driveperf.pipeline.capture.CaptureFixtures.wallAt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** F-CAP-16 AC2: steady cruise, Low Power Mode, low battery and heat lower the IMU rate, and the trip notes it. */
class CaptureBackoffTest {
    private val m = TripMachine()
    private val cfg = CaptureConfig()

    private fun recording(power: PowerStatus? = null): MachineState {
        val s = MachineState(parking = parking, power = power)
        val w = WakeEvent(WakeKind.MANUAL, ns(0.0), wallAt(0.0))
        return m.reduce(s, MachineEvent.Wake(ns(0.0), wallAt(0.0), w, header(0.0, WakeKind.MANUAL))).state
    }

    /** One fix a second from [fromS] to [toS]; [course] gives the heading at each second. */
    private fun cruise(s: MachineState, fromS: Int, toS: Int, speed: (Int) -> Double, course: (Int) -> Double = { 0.0 }): Transition {
        var t = Transition(s)
        val effects = mutableListOf<MachineEffect>()
        for (i in fromS..toS) {
            val f: LocationSample = fix(i.toDouble(), speed(i), i * 20.0).copy(courseDeg = course(i))
            t = m.reduce(t.state, MachineEvent.Fixes(ns(i.toDouble()), listOf(f)))
            effects += t.effects
        }
        return Transition(t.state, effects)
    }

    private fun Transition.rates(): List<Int> = effects.filterIsInstance<MachineEffect.SetImuRate>().map { it.hz }

    private fun Transition.tracked(): List<CaptureEvent> = effects.filterIsInstance<MachineEffect.Track>().map { it.event }

    private fun power(pct: Int = 80, lpm: Boolean = false, thermal: ThermalState = ThermalState.NOMINAL, charging: Boolean = false) =
        PowerStatus(pct, charging, lpm, thermal)

    @Test
    fun aMinuteOfSteadyCruiseDropsTheImuTo25Hz() {
        val before = cruise(recording(), 1, 60, { 20.0 })
        assertTrue(before.rates().isEmpty(), "59 s is not yet a minute")
        assertEquals(50, before.state.imuHz)
        val t = cruise(before.state, 61, 62, { 20.0 })
        assertEquals(listOf(25), t.rates())
        assertEquals(listOf(CaptureEvent.imuRate(25, RateReason.CRUISE)), t.tracked())
        assertTrue(t.effects.any { it is MachineEffect.Mark && it.type == CaptureMarkType.IMU_RATE && it.detail == "25" })
        assertTrue(t.state.cruise!!.steady)
    }

    @Test
    fun aManoeuvreRestoresTheFullRateAtOnce() {
        val cruising = cruise(recording(), 1, 62, { 20.0 }).state
        val brake = m.reduce(cruising, MachineEvent.Motion(ns(62.5), listOf(motion(62.5, accel = 0.2))))
        assertEquals(listOf(50), brake.rates())
        assertEquals(CaptureEvent.imuRate(50, RateReason.NOMINAL), brake.tracked().single())
        assertNull(brake.state.cruise)
        val gentle = m.reduce(cruising, MachineEvent.Motion(ns(62.5), listOf(motion(62.5, accel = 0.05))))
        assertTrue(gentle.rates().isEmpty(), "gentle motion keeps the cruise")
    }

    @Test
    fun speedSwingsTurnsAndCitySpeedsAreNotCruise() {
        val swinging = cruise(recording(), 1, 90, { if (it % 20 < 10) 18.0 else 22.0 })
        assertFalse(swinging.state.cruise?.steady ?: false, "a 4 m/s swing is outside the band")
        val bending = cruise(recording(), 1, 90, { 20.0 }, course = { it * 5.0 })
        assertFalse(bending.state.cruise?.steady ?: false, "5 °/s of turning")
        val city = cruise(recording(), 1, 90, { 12.0 })
        assertNull(city.state.cruise, "under 60 km/h")
        assertTrue((swinging.rates() + bending.rates() + city.rates()).isEmpty())
    }

    @Test
    fun cruiseAlsoTurnsTheHeadingWrapIntoAGentleTurn() {
        val wrapping = cruise(recording(), 1, 62, { 20.0 }, course = { if (it % 2 == 0) 359.5 else 0.5 })
        assertTrue(wrapping.state.cruise!!.steady, "359.5° to 0.5° is a 1° change, not 359°")
    }

    @Test
    fun powerAndHeatSetTheRateAndLeaveQualityNotes() {
        val s = recording()
        val lpm = m.reduce(s, MachineEvent.Power(ns(1.0), power(lpm = true)))
        assertEquals(listOf(25), lpm.rates())
        assertEquals(CaptureEvent.imuRate(25, RateReason.LOW_POWER), lpm.tracked().single())

        val serious = m.reduce(s, MachineEvent.Power(ns(1.0), power(thermal = ThermalState.SERIOUS)))
        assertEquals(listOf(25), serious.rates())
        assertTrue(serious.effects.any { it is MachineEffect.Mark && it.type == CaptureMarkType.THERMAL && it.detail == "serious" })
        val critical = m.reduce(serious.state, MachineEvent.Power(ns(2.0), power(thermal = ThermalState.CRITICAL)))
        assertEquals(listOf(10), critical.rates())
        assertEquals("thermal", critical.tracked().single().properties["reason"])
        val cooled = m.reduce(critical.state, MachineEvent.Power(ns(3.0), power()))
        assertEquals(listOf(50), cooled.rates())
        assertTrue(cooled.effects.any { it is MachineEffect.Mark && it.type == CaptureMarkType.THERMAL && it.detail == "nominal" })
        assertTrue(m.reduce(s, MachineEvent.Power(ns(1.0), power())).effects.isEmpty(), "nominal from the start: no note")
    }

    @Test
    fun lowBatteryIsNotedOnceAndChargingIsNotLow() {
        val s = recording()
        val low = m.reduce(s, MachineEvent.Power(ns(1.0), power(pct = 15)))
        assertEquals(listOf(25), low.rates())
        assertEquals(1, low.effects.count { it is MachineEffect.Mark && it.type == CaptureMarkType.LOW_BATTERY })
        val lower = m.reduce(low.state, MachineEvent.Power(ns(2.0), power(pct = 14)))
        assertTrue(lower.effects.none { it is MachineEffect.Mark && it.type == CaptureMarkType.LOW_BATTERY })
        val charging = m.reduce(s, MachineEvent.Power(ns(1.0), power(pct = 15, charging = true)))
        assertTrue(charging.effects.none { it is MachineEffect.Mark && it.type == CaptureMarkType.LOW_BATTERY })
    }

    @Test
    fun heatWinsOverCruiseAndCruiseIsNeverHigherThanPowerAllows() {
        val hot = recording(power(thermal = ThermalState.CRITICAL))
        assertEquals(cfg.imuIdleHz, hot.imuHz)
        val t = cruise(hot, 1, 62, { 20.0 })
        assertTrue(t.rates().isEmpty(), "10 Hz already, cruise does not raise it")
    }

    @Test
    fun theRateComesBackWhenATripResumesFromALongStop() {
        val moving = cruise(recording(), 1, 10, { 10.0 }).state
        val paused = cruise(moving, 11, 45, { 0.0 }).state
        assertEquals(RecorderState.PAUSED, paused.phase)
        val idle = m.reduce(paused, MachineEvent.Timer(ns(200.0), TimerId.IMU_IDLE))
        assertEquals(CaptureEvent.imuRate(cfg.imuIdleHz, RateReason.STATIONARY), idle.tracked().single())
        val resumed = cruise(idle.state, 201, 204, { 5.0 })
        assertEquals(RecorderState.RECORDING, resumed.state.phase)
        assertEquals(listOf(50), resumed.rates())
        assertTrue(resumed.effects.any { it is MachineEffect.Mark && it.type == CaptureMarkType.IMU_RATE && it.detail == "50" })
    }

    @Test
    fun afterARebootTheNextTripHasNoParkingSpot() {
        val boot = m.reduce(MachineState(), MachineEvent.Launch(ns(1.0), wallAt(1.0), true, parking, null, RearmReason.BOOT))
        assertNull(boot.state.parking, "EC-RBT: start at the first fix, no estimate")
        assertTrue(boot.effects.contains(MachineEffect.ArmParkedRegion(parking.point, cfg.parkedRadiusM)))
        val launch = m.reduce(MachineState(), MachineEvent.Launch(ns(1.0), wallAt(1.0), true, parking, null))
        assertEquals(parking, launch.state.parking)
    }
}
