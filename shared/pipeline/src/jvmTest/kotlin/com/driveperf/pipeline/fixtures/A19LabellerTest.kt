package com.driveperf.pipeline.fixtures

import com.driveperf.domain.trip.EventType
import com.driveperf.domain.capture.LocationSample
import com.driveperf.pipeline.score.Coverage
import com.driveperf.pipeline.score.EventHarness
import com.driveperf.pipeline.score.LabelledEvent
import com.driveperf.pipeline.score.TypeCounts
import com.driveperf.testing.FixtureCsv
import com.driveperf.testing.SyntheticCapture
import com.driveperf.testing.SyntheticCapture.Mount
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The A19 tool on a synthetic two-phone drive: the candidate in a tilted dash mount, the reference lying flat and
 * turned 40°, switched on 7 s earlier, with a wall clock 0.3 s off. Truth comes from the drive script.
 */
class A19LabellerTest {
    private class TwoPhones(
        val candidate: A19Labeller.Recording,
        val reference: A19Labeller.Recording,
        val truth: List<LabelledEvent>,
        val refStreams: SyntheticCapture.Streams,
    )

    private val drive: TwoPhones by lazy {
        val c = SyntheticCapture(Mount.rotated(Mount.PORTRAIT_UPRIGHT, 15.0, 10.0), startLat = 4.5, startLng = -29.5, seed = 44)
        val truth = LabelledFixtures.cityDrive(c)
        val r = SyntheticCapture(Mount.rotated(Mount.FLAT, 40.0, 3.0), startLat = 4.5, startLng = -29.5, seed = 7)
        r.cruise(PRE_ROLL_S)
        LabelledFixtures.cityDrive(r)
        val cs = c.streams()
        val rs = r.streams()
        TwoPhones(
            A19Labeller.Recording("cand", "ios", WALL, cs.fixes, cs.motion, cs.activity),
            A19Labeller.Recording("ref", "android", WALL - (PRE_ROLL_S * 1000).toLong() + CLOCK_ERROR_MS, rs.fixes, rs.motion, rs.activity),
            truth,
            rs,
        )
    }

    @Test
    fun theClocksAreAlignedFromTheMotionNotTheWallClock() {
        val sync = A19Labeller.sync(drive.candidate, drive.reference)
        assertEquals(-PRE_ROLL_S, sync.shiftS, 0.041, "wall clocks alone say ${(-PRE_ROLL_S * 1000 + CLOCK_ERROR_MS) / 1000}")
        assertTrue(sync.rho > 0.8, "rho ${sync.rho}")
        assertEquals(1, A19Labeller.pair(listOf(drive.candidate), listOf(drive.reference)).size)
    }

    @Test
    fun theReferenceFrameRecoversTheVehicleAxesWithoutThePipeline() {
        val f = A19Reference.frame(drive.reference.motion, drive.reference.fixes, drive.reference::tS)
        assertTrue(f.usable, "GNSS rho ${f.gnssRho}")
        val left = f.unstable.sumOf { it.endInclusive - it.start }
        assertTrue(left < 30.0, "only the 10 s stretch edges and the partial last window: ${f.unstable}")
        // Compare with the noise-free truth, box-averaged and filtered the same way (50 Hz → 25 Hz).
        val lon = drive.refStreams.lonTruthG
        val truth = A19Reference.a3(DoubleArray(f.n) { k -> (lon.getOrElse(2 * k) { 0.0 } + lon.getOrElse(2 * k + 1) { 0.0 }) / 2 })
        val rms = sqrt((0 until f.n).sumOf { (f.lon[it] - truth[it]).let { d -> d * d } } / f.n)
        assertTrue(rms < 0.03, "vehicle-frame RMS error $rms g")
    }

    @Test
    fun everyHardManoeuvreIsLabelledAndNothingElse() {
        val f = A19Reference.frame(drive.reference.motion, drive.reference.fixes, drive.reference::tS)
        val labels = A19Reference.labels(f).map { it.copy(tS = it.tS - PRE_ROLL_S) }
        for (t in drive.truth) {
            assertTrue(labels.any { it.type == t.type && abs(it.tS - t.tPeakS) <= EventHarness.TOLERANCE_S }, "no label for $t in $labels")
        }
        assertEquals(drive.truth.size, labels.size, labels.toString())
        assertTrue(labels.filter { it.type != EventType.CORNER }.none { it.uncertain }, "clear stops and pull-aways are certain: $labels")
    }

    @Test
    fun theEntryIsPrivacyTrimmedAndTheCandidateMeetsItsLabels() {
        val pairing = A19Labeller.pair(listOf(drive.candidate), listOf(drive.reference)).single()
        // A 100 m trim keeps the last hard stop, about 170 m before the end of the drive.
        val e = A19Labeller.entry(pairing, "rec-a19-01", trimM = 100.0) { id, csv -> base(id, csv) }
        val csv = checkNotNull(e.fixtureCsv) { e.summary }
        val labelsText = checkNotNull(e.labelsCsv)
        assertEquals(FixtureCsv.PRIVACY_HEADER, csv.lineSequence().first())
        assertEquals(FixtureCsv.PRIVACY_HEADER, labelsText.lineSequence().first())
        val parsed = FixtureCsv.parse(csv)
        assertTrue(parsed.fixes.all { it.lat in 0.0..5.0 && it.lng in -30.0..-25.0 }, "track inside the ocean test box")
        assertTrue(listOf("start_wall_ms", "parking").none { csv.contains(it) })
        assertTrue(parsed.motion.first().tMonoNs - SyntheticCapture.START_MONO_NS < 100_000_000, "rebased to t = 0")

        val trip = checkNotNull(runBlocking { PipelineHarness().run(LabelledSetTest.captureOf("rec-a19-01", parsed)) }.trip)
        val counts = EventHarness.total(
            listOf(
                EventHarness.evaluate(
                    EventHarness.of(trip.events), EventHarness.parseLabels(labelsText), coverage = EventHarness.parseCoverage(labelsText),
                ),
            ),
        )
        assertEquals(TypeCounts(3, 0, 0), counts[EventType.BRAKE], e.summary)
        assertEquals(TypeCounts(3, 0, 0), counts[EventType.ACCEL], e.summary)
        assertEquals(0, counts.getValue(EventType.CORNER).falsePositives + counts.getValue(EventType.CORNER).falseNegatives, e.summary)
    }

    @Test
    fun theTrimRemovesTheTrackEndsAndPreservesDistance() {
        val w = checkNotNull(A19Labeller.trimWindow(drive.candidate, 200.0))
        assertTrue(w.start > 0.0 && w.endInclusive < drive.candidate.endS)
        val csv = A19Labeller.fixture(drive.candidate, "x", w, 200.0, angleRad = 2.0)
        val kept = drive.candidate.fixes.filter { drive.candidate.tS(it.tMonoNs) in w }
        val out = FixtureCsv.parse(csv).fixes
        assertEquals(kept.size, out.size)
        fun len(fs: List<com.driveperf.domain.capture.LocationSample>) = fs.zipWithNext().sumOf { (a, b) ->
            val dy = (b.lat - a.lat) * 111_320.0
            val dx = (b.lng - a.lng) * 111_320.0 * kotlin.math.cos(Math.toRadians(a.lat))
            sqrt(dx * dx + dy * dy)
        }
        assertEquals(len(kept), len(out), len(kept) * 0.01)
        val course = kept.zip(out).firstNotNullOf { (a, b) -> if (a.courseDeg != null) a.courseDeg!! to b.courseDeg!! else null }
        assertEquals(((course.first - Math.toDegrees(2.0)) % 360 + 360) % 360, course.second, 0.06)
    }

    @Test
    fun anImuGapInsideAKeptStretchIsNotCovered() {
        val r = drive.reference
        val before = A19Reference.frame(r.motion, r.fixes, r::tS)
        val run = A19Reference.runs(before.ok).maxBy { it.count() }
        val gapAt = before.tS((run.first + run.last) / 2)
        val gapped = r.withoutMotion { t -> abs(t - gapAt) <= 0.5 }
        val after = A19Reference.frame(gapped.motion, gapped.fixes, gapped::tS)
        val hole = after.unstable.single { gapAt in it }
        // The 1 s gap plus a guard second each side, give or take a grid step.
        assertEquals(3.0, hole.endInclusive - hole.start, 0.2, after.unstable.toString())
        assertTrue(before.unstable.none { gapAt in it })
    }

    @Test
    fun aPairWithNoCoverageLeftIsSkippedNotWrittenAsFullyCovered() {
        // A 0.4 s IMU gap every 25 s: the reference still solves, but no covered run reaches 30 s.
        val gappy = drive.reference.withoutMotion { t -> t % 25.0 < 0.4 }
        val f = A19Reference.frame(gappy.motion, gappy.fixes, gappy::tS)
        assertTrue(f.usable, "the frame still solves: GNSS rho ${f.gnssRho}")
        val pairing = A19Labeller.pair(listOf(drive.candidate), listOf(gappy)).single()
        val e = A19Labeller.entry(pairing, "rec-a19-01", trimM = 100.0) { id, csv -> base(id, csv) }
        assertEquals(null, e.labelsCsv, e.summary)
        assertTrue("no reference coverage" in e.summary, e.summary)
    }

    @Test
    fun ratesAreNormalisedByTheCoveredDistanceOnly() {
        // 10 m/s due north for 60 s, one fix a second; 20 s of it covered, starting 5 s after the trip's start.
        val fixes = (0..60).map { s ->
            LocationSample(
                SyntheticCapture.START_MONO_NS + s * 1_000_000_000L, SyntheticCapture.WALL0 + s * 1000L,
                2.5 + s * 10.0 / 111_195.0, -27.5, null, 5.0, null, 10.0, null, 0.0, null,
            )
        }
        val coverage = Coverage(listOf(10.0..30.0))
        assertEquals(200.0, LabelledSetTest.coveredM(fixes, coverage, baseS = 5.0), 1.0)
        assertEquals(600.0, LabelledSetTest.coveredM(fixes, Coverage(), baseS = 5.0), 1.0)
    }

    private fun A19Labeller.Recording.withoutMotion(drop: (Double) -> Boolean) =
        A19Labeller.Recording(id, platform, startWallMs, fixes, motion.filterNot { drop(tS(it.tMonoNs)) }, activity)

    private fun base(id: String, csv: String): Double {
        val trip = checkNotNull(runBlocking { PipelineHarness().run(LabelledSetTest.captureOf(id, FixtureCsv.parse(csv))) }.trip)
        return (trip.trip.startedAt - SyntheticCapture.WALL0) / 1000.0
    }

    private companion object {
        const val PRE_ROLL_S = 7.0
        const val CLOCK_ERROR_MS = 300L
        const val WALL = 1_800_000_000_000L
    }
}
