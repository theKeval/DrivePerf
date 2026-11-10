package com.driveperf.platform.motion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MotionAssemblerTest {
    @Test
    fun `no sample until gravity and gyro have arrived`() {
        val assembler = MotionAssembler()
        assertNull(assembler.onLinearAcceleration(1, 0.0, 0.0, 0.0))
        assembler.onGravity(0.0, 0.0, 9.8)
        assertNull(assembler.onLinearAcceleration(2, 0.0, 0.0, 0.0))
        assembler.onGyro(0.0, 0.0, 0.0)
        assertNotNull(assembler.onLinearAcceleration(3, 0.0, 0.0, 0.0))
    }

    @Test
    fun `acceleration is converted from metres per second squared to g without a sign change`() {
        val assembler = primed()
        val sample = assembler.onLinearAcceleration(10, MotionAssembler.STANDARD_GRAVITY, -0.5 * MotionAssembler.STANDARD_GRAVITY, 0.0)!!
        assertEquals(1.0, sample.userAccelG.x, 1e-9)
        assertEquals(-0.5, sample.userAccelG.y, 1e-9)
        assertEquals(0.0, sample.userAccelG.z, 1e-9)
    }

    @Test
    fun `gravity is normalised to a unit vector pointing away from Earth`() {
        val assembler = MotionAssembler()
        assembler.onGravity(0.0, 0.0, 9.81) // phone face up at rest reads +g on z
        assembler.onGyro(0.0, 0.0, 0.0)
        val up = assembler.onLinearAcceleration(1, 0.0, 0.0, 0.0)!!.gravityUp
        assertEquals(1.0, up.z, 1e-9)
        assertEquals(0.0, up.x, 1e-9)
    }

    @Test
    fun `gravity of any magnitude comes out with length one`() {
        val assembler = MotionAssembler()
        assembler.onGravity(3.0, 4.0, 0.0)
        assembler.onGyro(0.0, 0.0, 0.0)
        val up = assembler.onLinearAcceleration(1, 0.0, 0.0, 0.0)!!.gravityUp
        assertEquals(1.0, Math.sqrt(up.x * up.x + up.y * up.y + up.z * up.z), 1e-9)
    }

    @Test
    fun `rotation rate is converted from radians to degrees per second`() {
        val assembler = MotionAssembler()
        assembler.onGravity(0.0, 0.0, 9.8)
        assembler.onGyro(Math.PI, 0.0, -Math.PI / 2)
        val rate = assembler.onLinearAcceleration(1, 0.0, 0.0, 0.0)!!.rotRateDps
        assertEquals(180.0, rate.x, 1e-9)
        assertEquals(-90.0, rate.z, 1e-9)
    }

    @Test
    fun `a missing quaternion scalar is reconstructed`() {
        val assembler = primed()
        assembler.onRotation(0.0, 0.0, Math.sin(Math.PI / 8), null) // 45 degree turn about z
        val q = assembler.onLinearAcceleration(1, 0.0, 0.0, 0.0)!!.attitude!!
        assertEquals(Math.cos(Math.PI / 8), q.w, 1e-9)
    }

    @Test
    fun `a zero gravity reading is ignored rather than producing NaN`() {
        val assembler = primed()
        assembler.onGravity(0.0, 0.0, 0.0)
        val up = assembler.onLinearAcceleration(1, 0.0, 0.0, 0.0)!!.gravityUp
        assertEquals(1.0, up.z, 1e-9) // kept the previous good reading
    }

    @Test
    fun `the sample carries the timestamp of the linear acceleration event`() {
        assertEquals(12_345L, primed().onLinearAcceleration(12_345, 0.0, 0.0, 0.0)!!.tMonoNs)
    }

    private fun primed() = MotionAssembler().also {
        it.onGravity(0.0, 0.0, 9.8)
        it.onGyro(0.0, 0.0, 0.0)
    }
}
