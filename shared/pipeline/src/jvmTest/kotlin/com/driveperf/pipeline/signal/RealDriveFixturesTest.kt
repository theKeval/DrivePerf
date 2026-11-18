package com.driveperf.pipeline.signal

import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.GravityTracker
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.capture.WakeKind
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.pipeline.fixtures.FixtureGolden
import com.driveperf.pipeline.fixtures.PipelineHarness
import com.driveperf.testing.FixtureCsv
import com.driveperf.testing.SyntheticCapture
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * DRIVE-160 regression on two privacy-trimmed real drives (8 min each, `shared/testing/fixtures/drives/rec-*`): an
 * iPhone drive aligns; the Android drive, recorded while the adapter still used the platform's fused gravity, does not,
 * and aligns once gravity is split out of the specific force without absorbing the car's acceleration.
 *
 * `-PregenerateFixtures=true` rewrites the goldens after a deliberate pipeline change.
 */
class RealDriveFixturesTest {
    private val dir = File(System.getProperty("driveperf.fixtures"))
    private val regenerate = System.getProperty("driveperf.regenerateFixtures") == "true"
    private val cfg = SignalConfig()

    @Test
    fun recordedFixturesArePrivacyTrimmed() {
        for (id in listOf(IOS, ANDROID)) {
            val file = File(dir, "$id.csv")
            val text = file.readText()
            val parsed = FixtureCsv.parse(text)
            assertEquals("recorded-trimmed", parsed.meta["source"])
            assertTrue((parsed.meta["trim_m"]?.toInt() ?: 0) >= MIN_TRIM_M, "$id trims at least 1000 m at each end")
            assertEquals("translate+rotate", parsed.meta["transform"])
            assertTrue(parsed.fixes.all { it.lat in 0.0..5.0 && it.lng in -30.0..-25.0 }, "$id lies in the ocean test box")
            assertTrue(file.length() <= MAX_BYTES, "$id is ${file.length()} bytes (limit 2 MB)")
            assertTrue((parsed.motion.last().tMonoNs - parsed.motion.first().tMonoNs) / NS <= MAX_SECONDS)
            val allowed = setOf("dpfx", "id", "source", "level", "platform", "imu_hz", "trim_m", "transform", "note")
            assertEquals(allowed, parsed.meta.keys, "$id carries no dates or other metadata")
        }
    }

    @Test
    fun theIphoneDriveAligns() = runTest {
        val capture = capture(IOS)
        assertTrue(SignalPipeline(cfg).process(capture, null).anyEpochConverged)
        val trip = PipelineHarness().trip(capture)
        assertNotEquals(QualityBadge.UNSCORABLE, trip.trip.quality?.badge)
        checkGolden(IOS, FixtureGolden.write(IOS, trip))
    }

    @Test
    fun theIphoneDriveStillAlignsThroughTheAndroidGravityTracker() {
        val capture = capture(IOS).let { it.copy(motion = retracked(it.motion)) }
        val sig = SignalPipeline(cfg).process(capture, null)
        assertTrue(sig.anyEpochConverged)
    }

    @Test
    fun theAndroidDriveRecordedWithPlatformGravityNeverAligns() = runTest {
        val capture = capture(ANDROID)
        assertFalse(SignalPipeline(cfg).process(capture, null).anyEpochConverged)
        val trip = PipelineHarness().trip(capture)
        assertEquals(QualityReason.NOT_ALIGNED, trip.trip.quality?.reason)
        checkGolden(ANDROID, FixtureGolden.write(ANDROID, trip))
    }

    @Test
    fun theAndroidDriveAlignsOnceGravityStopsAbsorbingAcceleration() = runTest {
        val raw = capture(ANDROID)
        val capture = raw.copy(motion = resplit(raw.motion))
        val sig = SignalPipeline(cfg).process(capture, null)
        assertTrue(sig.anyEpochConverged)
        val n = sig.trace.lonG.size
        val whole = Alignment(cfg).diagnose(DeviceSignal.resample(capture.motion, sig.t0Ns, n, cfg), 0 until n, sig.fixes, 0.0)
        assertTrue(whole.coherence >= cfg.alignMinCoherence, "coherence ${whole.coherence}")
        assertNotEquals(QualityBadge.UNSCORABLE, PipelineHarness().trip(capture).trip.quality?.badge)
    }

    private fun capture(id: String): CapturedTrip {
        val p = FixtureCsv.parse(File(dir, "$id.csv").readText())
        val header = CaptureHeader(
            id, SyntheticCapture.START_MONO_NS, SyntheticCapture.WALL0, 0, "UTC", WakeKind.MANUAL, null, NOMINAL_HZ, null,
        )
        return CapturedTrip(header, p.fixes, p.motion, p.activity, emptyList(), emptyList())
    }

    private fun checkGolden(id: String, golden: String) {
        val file = File(dir, "$id.golden.json")
        if (regenerate) file.writeText(golden)
        assertEquals(golden, file.readText(), "$id.golden.json differs from the pipeline output")
    }

    /** The recorded specific force and gyroscope replayed through the Android adapter's tracker. */
    private fun retracked(m: List<MotionSample>): List<MotionSample> {
        val tracker = GravityTracker()
        return m.map { s ->
            val split = tracker.update(s.tMonoNs, s.userAccelG + s.gravityUp, s.rotRateDps)
            s.copy(userAccelG = split.userAccelG, gravityUp = split.gravityUp)
        }
    }

    /**
     * The recorded specific force split again with a centred 60 s mean for gravity. The tracker cannot be replayed on
     * this file: the old adapter paired accelerometer samples with gyroscope readings up to 2 s stale.
     */
    private fun resplit(m: List<MotionSample>): List<MotionSample> {
        val f = m.map { it.userAccelG + it.gravityUp }
        val prefix = Array(f.size + 1) { Vec3(0.0, 0.0, 0.0) }
        for (i in f.indices) prefix[i + 1] = prefix[i] + f[i]
        val halfNs = (RESPLIT_S / 2 * NS).toLong()
        var lo = 0
        var hi = 0
        return m.mapIndexed { i, s ->
            while (m[lo].tMonoNs < s.tMonoNs - halfNs) lo++
            while (hi < m.size && m[hi].tMonoNs <= s.tMonoNs + halfNs) hi++
            val g = (prefix[hi] - prefix[lo]) * (1.0 / (hi - lo))
            val norm = kotlin.math.sqrt(g.x * g.x + g.y * g.y + g.z * g.z)
            s.copy(userAccelG = f[i] - g, gravityUp = g * (1.0 / norm))
        }
    }

    private operator fun Vec3.plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)

    private operator fun Vec3.minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)

    private operator fun Vec3.times(k: Double) = Vec3(x * k, y * k, z * k)

    private companion object {
        const val IOS = "rec-ios-commute-01"
        const val ANDROID = "rec-android-commute-01"
        const val MIN_TRIM_M = 1000
        const val MAX_BYTES = 2L * 1024 * 1024
        const val MAX_SECONDS = 600.0
        const val NS = 1e9
        const val NOMINAL_HZ = 50
        const val RESPLIT_S = 60.0
    }
}
