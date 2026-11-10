package com.driveperf.pipeline.score

import com.driveperf.domain.score.Band
import com.driveperf.domain.score.Calibrating
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.EfficiencyRating
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.InaccurateReason
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.trip.TurnDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TripScorerTest {
    private val analyser = R1Analyser()
    private val scorer = TripScorer()
    private val subs = mapOf(ContributorId.BRAKING to 90.0, ContributorId.ACCELERATION to 90.0, ContributorId.CORNERING to 90.0)

    private fun ctx(priors: List<PriorTrip> = emptyList(), scorable: Boolean = true, routeId: String? = null) =
        ScoringContext("t1", scorable, baselineEligible = true, distanceM = 5_000.0, routeId = routeId, priors = priors, nowWallMs = 1L)

    private fun prior(i: Int, smoothness: Int, context: DriveContext = DriveContext.CITY, routeId: String? = null, sub: Double = 90.0) =
        PriorTrip(
            "p$i", i * 1000L, context, routeId, smoothness, subs.mapValues { sub },
            stopsPerKm = 1.0, abruptAccelsPerMin = 0.2, speedWobbleMps = 0.5,
        )

    @Test
    fun g15TheFirstThreeTripsCalibrateThenABaselineApplies() {
        val priors = mutableListOf<PriorTrip>()
        val seen = (0 until 4).map { i ->
            Baselines.compare(80, subs, DriveContext.CITY, null, priors.toList()).also { priors += prior(i, 80 + i) }
        }
        assertEquals(listOf(Calibrating(1, 3), Calibrating(2, 3), Calibrating(3, 3)), seen.take(3).map { it.calibrating })
        val fourth = seen[3]
        assertNull(fourth.calibrating)
        assertEquals(ScoreBasis.CONTEXT, fourth.basis)
        assertEquals(81, fourth.baseline)
        assertEquals(-1, fourth.delta)
    }

    @Test
    fun anotherContextCalibratesSeparatelyAndAnEvenMedianRoundsHalfUp() {
        val city = (0 until 4).map { prior(it, listOf(80, 81, 90, 91)[it]) }
        val highway = Baselines.compare(70, subs, DriveContext.HIGHWAY, null, city)
        assertEquals(Calibrating(1, 3), highway.calibrating)
        assertEquals(86, Baselines.compare(70, subs, DriveContext.CITY, null, city).baseline, "median of 81 and 90 is 85.5, half-up")
    }

    @Test
    fun aRouteWithThreePriorRunsIsTheBasisAndOnlyTheLastTenCount() {
        val runs = (0 until 12).map { prior(it, if (it < 2) 40 else 90, routeId = "r1") }
        val r = Baselines.compare(85, subs, DriveContext.CITY, "r1", runs)
        assertEquals(ScoreBasis.ROUTE, r.basis)
        assertEquals("r1", r.basisRouteId)
        assertEquals(90, r.baseline)
        assertEquals(10, r.basisTrips.size)
    }

    @Test
    fun contributorBandsUseSixPoints() {
        val priors = (0 until 3).map { prior(it, 80, sub = 80.0) }
        val r = Baselines.compare(
            80, mapOf(ContributorId.BRAKING to 86.0, ContributorId.ACCELERATION to 74.0, ContributorId.CORNERING to 85.0),
            DriveContext.CITY, null, priors,
        )
        assertEquals(Band.SMOOTHER, r.bands[ContributorId.BRAKING])
        assertEquals(Band.ROUGHER, r.bands[ContributorId.ACCELERATION])
        assertEquals(Band.USUAL, r.bands[ContributorId.CORNERING])
    }

    @Test
    fun eligibilityFollowsSection57() {
        val quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 100.0, null, null, 1.0, 0.0, 1.0, emptyList())
        val trip = Trip(
            id = "t", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = 0L,
            startTzOffsetMin = 0, quality = quality, createdAt = 0L, updatedAt = 0L,
        )
        assertTrue(Baselines.eligible(trip, 80))
        assertFalse(Baselines.eligible(trip, null))
        assertFalse(Baselines.eligible(trip.copy(kind = TripKind.PASSENGER), 80))
        assertFalse(Baselines.eligible(trip.copy(quality = quality.copy(badge = QualityBadge.LIMITED)), 80), "D3")
        assertFalse(Baselines.eligible(trip.copy(suspect = Suspect.TRANSIT), 80), "D7")
        assertTrue(Baselines.eligible(trip.copy(suspect = Suspect.TRANSIT, suspectAnswer = SuspectAnswer.NO), 80))
        assertNull(Baselines.median(emptyList()))
    }

    @Test
    fun efficiencyCompares_withinTheContextOnly() {
        val priors = (0 until 3).map { prior(it, 80) }
        fun rate(stops: Int, eligible: Boolean = true, context: DriveContext = DriveContext.CITY) =
            EfficiencyHabits.rate(stops, 5_000.0, 1, 300.0, 0.5, 0.05, context, eligible, priors)
        assertEquals(EfficiencyRating.USUAL, rate(5).rating)
        assertEquals(EfficiencyRating.BETTER, rate(0).rating)
        assertEquals(EfficiencyRating.WORSE, rate(15).rating)
        assertNull(rate(5, eligible = false).rating)
        assertNull(rate(5, context = DriveContext.HIGHWAY).rating, "calibrating in a new context")
        assertEquals(1.0, rate(5).stopsPerKm)
        assertEquals(0.2, rate(5).abruptAccelsPerMin)
        assertNull(EfficiencyHabits.rate(0, 0.0, 0, 0.0, null, null, DriveContext.CITY, true, priors).stopsPerKm)
    }

    @Test
    fun aRoughTripBecomesEventsWithIdsAndAScoreWithItsLedger() {
        val a = analyser.analyse(Goldens.g04)
        val out = scorer.score(a, ctx())
        val score = assertNotNull(out.score)
        assertEquals(24, out.events.size)
        assertEquals(24, out.events.map { it.id }.toSet().size)
        assertEquals(52, score.smoothness)
        assertEquals(Calibrating(1, 3), score.calibrating)
        assertEquals(24, score.ledger!!.perEvent.size)
        val braking = score.contributors.first { it.id == ContributorId.BRAKING }
        assertEquals(8, braking.eventIds.size)
        assertEquals(10.63, braking.value!!, 0.005)
        assertNull(braking.band)
        val consistency = score.contributors.firstOrNull { it.id == ContributorId.CONSISTENCY }
        assertTrue(consistency == null || consistency.eventIds.isEmpty())
        val corner = out.events.first { it.type == EventType.CORNER }
        assertEquals(TurnDirection.LEFT, corner.direction, "the first city corner turns left (negative lateral)")
        assertEquals(TurnDirection.RIGHT, out.events.filter { it.type == EventType.CORNER }[1].direction)
        assertNull(corner.lat, "no position in the synthetic GNSS: listed, not pinned")
        assertTrue(out.efficiencyInputs.stopStartCount >= 7)
    }

    @Test
    fun feedbackRescoresFromTheLedgerAlone() {
        val a = analyser.analyse(Goldens.g04)
        val out = scorer.score(a, ctx())
        val brake = out.events.first { it.type == EventType.BRAKE }
        val needed = mapOf(brake.id to EventFeedback(brake.id, "t1", needed = true, inaccurate = null, at = 2L))
        val rescored = scorer.rescore(out.score!!, out.events, needed, DriveContext.CITY, ctx(), out.efficiencyInputs)
        assertEquals(54, rescored.smoothness, "G-08")
        assertFalse(brake.id in rescored.contributors.first { it.id == ContributorId.BRAKING }.eventIds)
        val original = assertNotNull(out.score)
        val inaccurate = mapOf(brake.id to EventFeedback(brake.id, "t1", needed = false, inaccurate = InaccurateReason.POTHOLE, at = 2L))
        assertEquals(54, scorer.rescore(original, out.events, inaccurate, DriveContext.CITY, ctx(), out.efficiencyInputs).smoothness)
        val none = scorer.rescore(original, out.events, emptyMap(), DriveContext.CITY, ctx(), out.efficiencyInputs)
        assertEquals(52, none.smoothness)
    }

    @Test
    fun aPassengerOrUnscorableTripHasEventsButNoScore() {
        val out = scorer.score(analyser.analyse(Goldens.g04), ctx(scorable = false))
        assertNull(out.score)
        assertEquals(24, out.events.size)
    }

    @Test
    fun aTripUnder120SecondsOfExposureHasNoScore() {
        val short = Goldens.trace(com.driveperf.testing.SyntheticDrive(v0 = 13.9).apply { hold(100.0) }.build())
        assertNull(scorer.score(analyser.analyse(short), ctx()).score)
    }

    @Test
    fun eventsArePinnedToTheNearestFixWithin3Seconds() {
        val base = Goldens.g10
        val pinned = R1Trace(
            base.lonG, base.latG, base.vertG, base.yawDps, base.quality,
            base.gnss.map { if (it.tS in 55.0..65.0) it.copy(lat = 2.0, lng = -27.5) else it },
        )
        val event = scorer.score(analyser.analyse(pinned), ctx()).events.single()
        assertEquals(2.0, event.lat)
        assertTrue(event.speedBeforeMps!! > event.speedAfterMps!!)
    }

    @Test
    fun stopMetricsMergeShortMotionAndCountAbruptRuns() {
        val moving = BooleanArray(25 * 60) { k -> k !in 250 until 500 && k !in 520 until 700 }
        assertEquals(1, StopMetrics.stops(moving, 25, 10.0).size, "split by under 3 s of motion")
        val a3 = DoubleArray(100) { k -> if (k in 10 until 40) 0.2 else 0.0 }
        assertEquals(1, StopMetrics.abruptAccels(a3, { true }, 25))
        assertEquals(0, StopMetrics.abruptAccels(DoubleArray(100) { k -> if (k in 10 until 20) 0.2 else 0.0 }, { true }, 25))
    }
}
