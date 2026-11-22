package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.trip.EventType
import com.driveperf.foundation.Units
import com.driveperf.testing.SyntheticCapture
import java.io.File
import java.nio.file.Files
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The A19 reference's forward-axis solve, its event rubric and the set's entry IDs, on hand-made inputs. */
class A19RubricTest {
    @Test
    fun theForwardAxisIsSolvedNotLeanedTowardsCorrelatedLateralMotion() {
        // Lateral acceleration that tracks the longitudinal one (a car braking into every bend): Hᵀa alone tilts ~45°.
        val n = (600 * A19Reference.FS).toInt()
        fun t(k: Int) = k / A19Reference.FS
        val lon = DoubleArray(n) { 0.2 * sin(0.3 * t(it)) + 0.1 * sin(0.71 * t(it)) }
        val lat = DoubleArray(n) { 0.9 * lon[it] + 0.05 * sin(1.3 * t(it)) }
        val theta = 0.7
        val h1 = DoubleArray(n) { lon[it] * cos(theta) + lat[it] * sin(theta) }
        val h2 = DoubleArray(n) { lon[it] * sin(theta) - lat[it] * cos(theta) }
        val perS = A19Reference.FS.toInt()
        val dvdt = (2 until 598).map { s ->
            s.toDouble() to (0 until perS).map { lon[((s - 0.5) * A19Reference.FS).toInt() + it] }.average()
        }
        val (u, rho) = A19Reference.forward(h1, h2, dvdt, 0.0)
        val errDeg = Math.toDegrees(abs(atan2(u.second, u.first) - theta))
        assertTrue(errDeg < 1.0, "forward axis off by $errDeg°")
        assertTrue(rho > 0.99, "rho $rho")
    }

    @Test
    fun theForwardAxisIsFittedAtTheGnssLagNotAcrossIt() {
        // GNSS dv/dt 1 s late, and lateral motion that follows the longitudinal by 1 s (brake, then turn): fitted across
        // the lag, the lateral channel explains the late GNSS best and the axis swings far round. (A single sinusoid
        // cannot tell lag from rotation: every lag fits it perfectly, so the drive here is broadband.)
        val fs = A19Reference.FS
        val n = (600 * fs).toInt()
        fun lon(t: Double) = 0.2 * sin(0.3 * t) + 0.1 * sin(0.71 * t) + 0.05 * sin(1.37 * t)
        val theta = 0.4
        val lat = DoubleArray(n) { 0.8 * lon(it / fs - 1.0) + 0.03 * sin(2.1 * it / fs) }
        val h1 = DoubleArray(n) { lon(it / fs) * cos(theta) + lat[it] * sin(theta) }
        val h2 = DoubleArray(n) { lon(it / fs) * sin(theta) - lat[it] * cos(theta) }
        val dvdt = (3 until 597).map { s -> s.toDouble() to (0 until fs.toInt()).map { lon(s - 1.0 - 0.5 + it / fs) }.average() }
        val (u, rho) = A19Reference.forward(h1, h2, dvdt, 0.0)
        val errDeg = Math.toDegrees(abs(atan2(u.second, u.first) - theta))
        assertTrue(errDeg < 3.0, "forward axis off by $errDeg°")
        assertTrue(rho > 0.99, "rho $rho")
    }

    @Test
    fun twoBrakesTheAppKeepsApartAreTwoLabels() {
        // 0.40 g for 1 s, 0.19 g for 1.2 s (between the loose and exact exit), then 0.38 g for 1 s, at 15 m/s.
        val fs = A19Reference.FS
        val n = (30 * fs).toInt()
        val lon = DoubleArray(n) { k ->
            val t = k / fs
            when {
                t in 9.0..<10.0 -> -0.40
                t in 10.0..<11.2 -> -0.19
                t in 11.2..<12.2 -> -0.38
                else -> 0.0
            }
        }
        val speed = DoubleArray(n)
        speed[0] = 15.0
        for (k in 1 until n) speed[k] = speed[k - 1] + lon[k] * Units.G / fs
        val zero = DoubleArray(n)
        val f = A19Reference.Frame(0.0, lon, zero, zero, zero, speed, BooleanArray(n) { true }, 1.0, emptyList())
        val brakes = A19Reference.labels(f).filter { it.type == EventType.BRAKE }
        assertEquals(2, brakes.size, brakes.toString())
        assertEquals(listOf(false, false), brakes.map { it.uncertain }, brakes.toString())
        assertTrue(brakes[0].tS in 9.0..10.0 && brakes[1].tS in 11.2..12.2, brakes.toString())
    }

    @Test
    fun entryIdsFollowTheRecordingsNotTheirOrder() {
        val out = Files.createTempDirectory("a19-set").toFile()
        try {
            fun rec(id: String, wallMs: Long) = A19Labeller.Recording(id, "x", wallMs, emptyList(), MOTION, emptyList())
            fun pairing(c: String, r: String, wallMs: Long) = A19Labeller.Pairing(rec(c, wallMs), rec(r, wallMs), 600.0)
            val fake: (A19Labeller.Pairing, String) -> A19Labeller.Entry = { p, id ->
                A19Labeller.Entry(id, "csv ${p.candidate.id}", "labels ${p.candidate.id}", "$id ${p.candidate.id}")
            }
            val later = pairing("cand-later", "ref-later", 2_000_000L)
            A19LabelTool.build(listOf(later), out, fake)
            // The earlier drive becomes pairable on the next run: it sorts first but must take a new ID.
            val earlier = pairing("cand-earlier", "ref-earlier", 1_000_000L)
            val log = A19LabelTool.build(listOf(earlier, later), out, fake)
            assertEquals("labels cand-later", File(out, "rec-a19-01.labels.csv").readText())
            assertEquals("labels cand-earlier", File(out, "rec-a19-02.labels.csv").readText())
            assertTrue(log.any { "rec-a19-01" in it && "KEPT" in it }, log.toString())
            assertEquals(
                listOf("rec-a19-01 cand-later ref-later", "rec-a19-02 cand-earlier ref-earlier"),
                File(out, "a19-map.txt").readLines(),
            )
        } finally {
            out.deleteRecursively()
        }
    }

    private companion object {
        val MOTION = listOf(
            MotionSample(SyntheticCapture.START_MONO_NS, Vec3(0.0, 0.0, 0.0), Vec3(0.0, 0.0, 1.0), Vec3(0.0, 0.0, 0.0), null),
        )
    }
}
