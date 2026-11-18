package com.driveperf.platform.motion

import com.driveperf.domain.capture.MotionSample
import com.driveperf.foundation.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MotionAssemblerTest {
    @Test
    fun `an accelerometer event waits until a gyroscope event covers its time`() {
        val assembler = assembler()
        assertTrue(assembler.onAccelerometer(MS * 10, 0.0, 0.0, Units.G).isEmpty())
        assertTrue(assembler.onGyro(MS * 5, 0.0, 0.0, 0.0).isEmpty())
        assertEquals(listOf(MS * 10), assembler.onGyro(MS * 15, 0.0, 0.0, 0.0).map { it.tMonoNs })
    }

    @Test
    fun `a phone face up at rest reads gravity up and no user acceleration`() {
        val sample = assembler().also { it.onGyro(0, 0.0, 0.0, 0.0) }.onAccelerometer(0, 0.0, 0.0, 9.81).single()
        assertEquals(1.0, sample.gravityUp.z, 1e-9)
        assertEquals(0.0, sample.userAccelG.z, 1e-9)
    }

    @Test
    fun `a batched burst is released in order with the rotation rate interpolated at each accelerometer time`() {
        val assembler = assembler()
        val accel = (0 until 25).flatMap { assembler.onAccelerometer(it * 20 * MS, 0.0, 0.0, Units.G) }
        assertTrue(accel.isEmpty())
        val out = assembler.onGyro(0, 0.0, 0.0, 0.0) + assembler.onGyro(500 * MS, PI, 0.0, -PI / 2)
        assertEquals((0 until 25).map { it * 20 * MS }, out.map { it.tMonoNs })
        assertEquals(180.0 * 240 / 500, out[12].rotRateDps.x, 1e-6)
        assertEquals(-90.0 * 240 / 500, out[12].rotRateDps.z, 1e-6)
    }

    @Test
    fun `without a covering gyroscope event a sample is released after the wait limit with the latest rate`() {
        val assembler = MotionAssembler(NOW, maxWaitNs = 100 * MS)
        assembler.onGyro(0, 0.0, 0.0, 1.0)
        assertTrue(assembler.onAccelerometer(50 * MS, 0.0, 0.0, Units.G).isEmpty())
        val released = assembler.onAccelerometer(200 * MS, 0.0, 0.0, Units.G)
        assertEquals(listOf(50 * MS), released.map { it.tMonoNs })
        assertEquals(1.0 * MotionAssembler.RAD_TO_DEG, released.single().rotRateDps.z, 1e-9)
    }

    @Test
    fun `sustained braking stays in the user acceleration instead of tilting gravity`() {
        val assembler = assembler()
        val samples = run(assembler, fromS = 0.0, toS = 40.0) { _ -> Triple(0.0, 0.0, Units.G) } +
            run(assembler, fromS = 40.0, toS = 45.0) { _ -> Triple(-0.3 * Units.G, 0.0, Units.G) }
        val last = samples.last()
        assertTrue("user acceleration kept: ${last.userAccelG.x}", last.userAccelG.x < -0.25)
        assertEquals(1.0, last.gravityUp.z, 0.01)
    }

    @Test
    fun `turning the phone is followed by the gyroscope`() {
        val assembler = assembler()
        run(assembler, fromS = 0.0, toS = 40.0) { _ -> Triple(0.0, 0.0, Units.G) }
        // Turn the phone 90° counter-clockwise about y in 1 s: a fixed world vector turns the other way in the device
        // frame, so gravity moves from +z to −x.
        val rateRadS = PI / 2
        val samples = (0..50).flatMap { k ->
            val t = 40.0 + k * 0.02
            val angle = minOf(rateRadS * (t - 40.0), PI / 2)
            val ns = (t * 1e9).toLong()
            assembler.onAccelerometer(ns, -sin(angle) * Units.G, 0.0, cos(angle) * Units.G) + assembler.onGyro(ns, 0.0, rateRadS, 0.0)
        }
        val up = samples.last().gravityUp
        assertEquals(-1.0, up.x, 0.02)
        assertEquals(0.0, up.z, 0.05)
        assertTrue(samples.all { it.userAccelG.x * it.userAccelG.x + it.userAccelG.z * it.userAccelG.z < 0.05 * 0.05 })
    }

    @Test
    fun `a missing quaternion scalar is reconstructed`() {
        val assembler = assembler()
        assembler.onRotation(0.0, 0.0, sin(PI / 8), null) // 45 degree turn about z
        assembler.onGyro(0, 0.0, 0.0, 0.0)
        val q = assembler.onAccelerometer(0, 0.0, 0.0, Units.G).single().attitude
        assertEquals(cos(PI / 8), q?.w ?: 0.0, 1e-9)
    }

    @Test
    fun `out of order events are ignored`() {
        val assembler = assembler()
        assembler.onGyro(100 * MS, 0.0, 0.0, 0.0)
        assertEquals(1, assembler.onAccelerometer(50 * MS, 0.0, 0.0, Units.G).size)
        assertTrue(assembler.onAccelerometer(40 * MS, 0.0, 0.0, Units.G).isEmpty())
    }

    @Test
    fun `a gyroscope burst that arrives before its accelerometer burst is still interpolated`() {
        val assembler = assembler()
        assembler.onGyro(0, 0.0, 0.0, 0.0)
        assembler.onAccelerometer(0, 0.0, 0.0, Units.G)
        // The sensor hub flushes 0.5 s of gyroscope first, ramping 0 → 1 rad/s about z, then the accelerometer.
        for (k in 1..25) assertTrue(assembler.onGyro(k * 20 * MS, 0.0, 0.0, k / 25.0).isEmpty())
        val out = (1..25).flatMap { k -> assembler.onAccelerometer(k * 20 * MS - 10 * MS, 0.0, 0.0, Units.G) }
        assertEquals(25, out.size)
        for ((i, sample) in out.withIndex()) {
            val expected = (i + 0.5) / 25.0 * MotionAssembler.RAD_TO_DEG
            assertEquals(expected, sample.rotRateDps.z, 1e-6)
        }
    }

    @Test
    fun `a first accelerometer event from the future is dropped and does not block the ones after it`() {
        val now = 10_000 * MS
        val assembler = MotionAssembler({ now })
        assembler.onGyro(now, 0.0, 0.0, 0.0)
        assertTrue(assembler.onAccelerometer(now + 300_000 * MS, 0.0, 0.0, Units.G).isEmpty())
        assertTrue(assembler.onGyro(now + 300_000 * MS, 0.0, 0.0, 0.0).isEmpty())
        assertEquals(listOf(now - 10 * MS), assembler.onAccelerometer(now - 10 * MS, 0.0, 0.0, Units.G).map { it.tMonoNs })
        assertEquals(listOf(now + 10 * MS), (assembler.onAccelerometer(now + 10 * MS, 0.0, 0.0, Units.G) +
            assembler.onGyro(now + 20 * MS, 0.0, 0.0, 0.0)).map { it.tMonoNs })
    }

    private fun assembler() = MotionAssembler(NOW)

    /** Feeds 50 Hz accelerometer and gyroscope (at rest) events from [fromS] until [toS]. */
    private fun run(
        assembler: MotionAssembler,
        fromS: Double,
        toS: Double,
        accel: (Double) -> Triple<Double, Double, Double>,
    ): List<MotionSample> {
        val out = mutableListOf<MotionSample>()
        var t = fromS
        while (t < toS) {
            val ns = (t * 1e9).toLong()
            val (x, y, z) = accel(t)
            out += assembler.onAccelerometer(ns, x, y, z)
            out += assembler.onGyro(ns, 0.0, 0.0, 0.0)
            t += STEP_S
        }
        return out
    }

    private companion object {
        val NOW: () -> Long = { Long.MAX_VALUE / 2 }
        const val MS = 1_000_000L
        const val STEP_S = 0.02
    }
}
