package com.driveperf.presentation.drives

import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.score.Band
import com.driveperf.domain.score.Calibrating
import com.driveperf.domain.score.Contributor
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.RecordState
import com.driveperf.domain.score.RouteName
import com.driveperf.domain.score.Score
import com.driveperf.domain.score.ScoreBasis
import com.driveperf.domain.score.ScoreLine
import com.driveperf.domain.score.ScoreLines
import com.driveperf.domain.score.ScoreReader
import com.driveperf.domain.score.Tip
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.score.TripScoreDetail
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.QualityReason
import com.driveperf.domain.trip.RawState
import com.driveperf.domain.trip.Stretch
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripSource
import com.driveperf.presentation.score.ComparisonTone
import com.driveperf.presentation.score.ExplainerSectionKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A score reader over fixed rows, as the tests set them. */
class FakeScoreReader : ScoreReader {
    val lines = MutableStateFlow(ScoreLines.EMPTY)
    val details = MutableStateFlow<Map<String, TripScoreDetail>>(emptyMap())
    val opened = mutableListOf<String>()

    override fun observeLines(): Flow<ScoreLines> = lines

    override fun observeDetail(tripId: String): Flow<TripScoreDetail> {
        opened += tripId
        return details.map { it[tripId] ?: TripScoreDetail(tripId, null, emptyList(), emptyMap(), null, null, null, emptyList()) }
    }
}

class RecordingAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    override fun track(event: AnalyticsEvent) {
        events += event
    }
    override fun setUsageConsent(allowed: Boolean) = Unit
    override fun resetId() = Unit
    fun named(name: String) = events.filter { it.name == name }.map { it.properties }
}

/** The score surfaces on the Drives screens (DRIVE-77, DRIVE-82, DRIVE-87, DRIVE-55, DRIVE-113, DRIVE-126). */
@OptIn(ExperimentalCoroutinesApi::class)
class DrivesScoresTest {
    private val trips = MutableStateFlow<List<Trip>>(emptyList())
    private val reader = FakeScoreReader()
    private val analytics = RecordingAnalytics()
    private val clock = FixedClock(toronto(2026, 11, 10, 18))

    @BeforeTest fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.presenter() =
        DrivesPresenter(trips, null, MutableStateFlow(UnitSystem.METRIC), JavaTimeFormatter(), clock, backgroundScope, reader, analytics)

    private val route = RouteName("r1", null, RoutePreset.COMMUTE)
    private val brake1 = DrivingEvent("e1", "t", EventType.BRAKE, 120.0, 119.0, 121.4, 1.4, -0.42, null, null, null, 14.0, 0.0,
        Confidence.HIGH, "smooth-1.0.0")
    private val brake2 = brake1.copy(id = "e2", tPeakS = 600.0, peakG = -0.35, confidence = Confidence.LOW)
    private val corner = brake1.copy(id = "e3", type = EventType.CORNER, tPeakS = 300.0, peakG = 0.4, durationS = 2.0)

    private fun contributors(calibrating: Boolean = false) = listOf(
        Contributor(ContributorId.BRAKING, 70.0, 1.0, Band.ROUGHER.takeUnless { calibrating },
            "braking.rougher".takeUnless { calibrating }, listOf("e2", "e1")),
        Contributor(ContributorId.ACCELERATION, 95.0, 0.1, Band.SMOOTHER.takeUnless { calibrating },
            "acceleration.smoother".takeUnless { calibrating }, emptyList()),
        Contributor(ContributorId.CORNERING, 88.0, 0.4, Band.USUAL.takeUnless { calibrating },
            "cornering.usual".takeUnless { calibrating }, listOf("e3")),
        Contributor(ContributorId.CONSISTENCY, 90.0, 0.2, Band.SMOOTHER.takeUnless { calibrating },
            "consistency.smoother".takeUnless { calibrating }, emptyList()),
    )

    private fun score(
        id: String = "t",
        smoothness: Int? = 85,
        basis: ScoreBasis? = ScoreBasis.ROUTE,
        calibrating: Calibrating? = null,
        record: RecordState? = null,
    ) = Score(
        id, smoothness, smoothness?.toDouble(), if (calibrating == null) 83 else null,
        if (calibrating == null) smoothness?.minus(83) else null,
        basis, "r1", calibrating, contributors(calibrating != null), null, null,
        Tip("brake_light", TipCategory.BRAKING, "e1", "p1"), record, "smooth-1.0.0", 1L,
    )

    @Suppress("LongParameterList") // one knob per part of the detail the tests vary
    private fun scored(t: Trip, s: Score, events: List<DrivingEvent> = listOf(brake1, brake2, corner), runs: List<Int> = emptyList(),
        ineligible: IneligibleReason? = null, feedback: Map<String, EventFeedback> = emptyMap()) {
        reader.details.value += t.id to TripScoreDetail(t.id, s, events.map { it.copy(tripId = t.id) }, feedback, route, ineligible,
            "GTI", runs)
    }

    @Test
    fun `cards show the score, the route and the car only with more than one car`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(
            trip("a", startedAt = toronto(2026, 11, 10, 8), quality = quality(QualityBadge.GOOD)),
            trip("b", startedAt = toronto(2026, 11, 9, 8), quality = quality(QualityBadge.UNSCORABLE)),
            trip("c", startedAt = toronto(2026, 11, 8, 8), quality = quality(QualityBadge.GOOD), kind = TripKind.PASSENGER),
            trip("d", startedAt = toronto(2026, 11, 7, 8), quality = quality(QualityBadge.GOOD)),
        )
        reader.lines.value = ScoreLines(
            mapOf(
                "a" to ScoreLine("a", 91, 7, null, newRecord = true, route = route, car = "GTI"),
                "b" to ScoreLine("b", null, null, null, false, null, "GTI"),
                "c" to ScoreLine("c", null, null, null, false, null, null),
                "d" to ScoreLine("d", 70, null, Calibrating(2, 3), false, null, null),
            ),
            carCount = 1,
        )
        val p = presenter()
        val rows = p.state.value.trips.associateBy { it.id }
        val a = rows.getValue("a")
        assertEquals("The Commute", a.title)
        assertEquals("Tuesday morning · 10 Nov", a.subtitle)
        assertEquals("91", a.score?.score)
        assertEquals("+7", a.score?.delta)
        assertEquals(ComparisonTone.BETTER, a.score?.tone)
        assertTrue(a.score!!.newRecord)
        assertNull(a.car)
        assertTrue("Smoothness 91, 7 above your usual. New route record" in a.accessibilityLabel)
        assertEquals("Not scored", rows.getValue("b").score?.notScoredText)
        assertNull(rows.getValue("b").score?.score)
        // Passenger trips carry no score (EC-PAS).
        assertNull(rows.getValue("c").score)
        assertEquals("Passenger", rows.getValue("c").kindLabel)
        // Calibrating: the score, no delta.
        assertEquals("70", rows.getValue("d").score?.score)
        assertNull(rows.getValue("d").score?.delta)
        assertEquals(ComparisonTone.CALIBRATING, rows.getValue("d").score?.tone)
        assertEquals("Saturday morning", rows.getValue("d").title)

        reader.lines.value = reader.lines.value.copy(carCount = 2)
        assertEquals("GTI", p.state.value.trips.first { it.id == "a" }.car)
    }

    @Test
    fun `week sections average their scored driver drives`() = runTest(UnconfinedTestDispatcher()) {
        trips.value = listOf(
            trip("a", startedAt = toronto(2026, 11, 10, 8)),
            trip("b", startedAt = toronto(2026, 11, 9, 8)),
            trip("c", startedAt = toronto(2026, 11, 9, 18), kind = TripKind.PASSENGER),
            trip("d", startedAt = toronto(2026, 11, 3, 8)),
        )
        reader.lines.value = ScoreLines(
            mapOf("a" to ScoreLine("a", 90, 1, null, false, null, null), "b" to ScoreLine("b", 81, 1, null, false, null, null)),
            0,
        )
        val sections = presenter().state.value.sections
        assertEquals(listOf("This week", "Last week"), sections.map { it.title })
        assertEquals("3 drives · avg 86", sections[0].meta)
        assertEquals("This week, 3 drives, average 86", sections[0].accessibilityLabel)
        assertEquals("1 drive", sections[1].meta)
    }

    @Test
    fun `trip detail shows the score, contributors, events and tip`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(quality = quality(QualityBadge.GOOD)).copy(context = DriveContext.CITY, cleanest = Stretch(9_480.0, 600.0, 1_100.0))
        trips.value = listOf(t)
        scored(t, score(), runs = listOf(80, 82, 85), feedback = mapOf("e2" to EventFeedback("e2", "t", true, null, 1L)))
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        val d = assertNotNull(p.state.value.detail)
        assertFalse(d.scoresHidden)
        assertEquals("GTI", d.meta?.car)
        assertEquals("Auto-detected", d.meta?.source)
        val s = assertNotNull(d.score)
        assertEquals(85, s.smoothness)
        assertEquals(83, s.baseline)
        assertEquals("+2 vs your usual on The Commute", s.comparison?.text)
        assertEquals("▲", s.comparison?.glyph)
        assertEquals("Smoothness 85. 2 above your usual on The Commute. Score based on 31 of 33 min.", s.accessibilityLabel)
        assertNull(s.paceNote)
        // Scored 31 of 33 min: part of the drive was left out.
        assertEquals("Score based on 31 of 33 min", s.basedOn)
        assertEquals(listOf("Braking", "Acceleration", "Cornering", "Consistency"), s.contributors.map { it.label })
        val braking = s.contributors[0]
        assertEquals("Rougher than usual", braking.bandText)
        assertEquals("▼", braking.bandGlyph)
        assertEquals("2 abrupt stops", braking.reason)
        assertEquals(listOf("e2", "e1"), braking.events.map { it.id })
        assertEquals("Braking, rougher than usual. 2 abrupt stops. Show events.", braking.accessibilityLabel)
        assertEquals("Steadier speed than usual", s.contributors[3].reason)

        val events = assertNotNull(d.events)
        assertEquals("Events · 3", events.title)
        assertEquals(listOf(2, 0, 1), events.counts.map { it.count })
        assertEquals(listOf("e1", "e3", "e2"), events.rows.map { it.id })
        assertEquals("0.42 g · 1.4 s", events.rows[0].meta)
        assertEquals("Hard braking", events.rows[0].label)
        assertEquals("Needed", events.rows[2].tag)
        assertTrue(events.rows[2].needed)
        assertTrue(events.rows[2].lowConfidence)
        assertNull(events.note)

        assertEquals("9.5 km with no abrupt inputs.", d.cleanest)
        val tip = assertNotNull(d.tip)
        assertEquals("One thing for next time", tip.kicker)
        assertTrue(tip.text.startsWith("Next time, ease off"))

        val b = assertNotNull(d.breakdown)
        assertEquals("Compared with your usual on The Commute: 83.", b.basisLine)
        assertEquals(listOf(true, false), b.options.map { it.selected })
        assertEquals("Your runs of The Commute", b.options[0].title)
        assertEquals("usual 83", b.options[0].value)
        assertEquals("Your usual city drives", b.options[1].title)
        assertEquals("Your last 3 runs of The Commute", b.runsTitle)
        assertEquals("Smoothness on your last 3 runs: 80, 82, 85.", b.runsSummary)
        assertNull(b.notScoredText)
    }

    @Test
    fun `a context comparison, a record and an unusual pace`() = runTest(UnconfinedTestDispatcher()) {
        val earlier = trip("t0", startedAt = toronto(2026, 10, 14, 8))
        val t = trip(quality = quality(QualityBadge.GOOD).copy(scoredS = 1_980.0)).copy(context = DriveContext.HIGHWAY)
        trips.value = listOf(t, earlier)
        scored(t, score(smoothness = 80, basis = ScoreBasis.CONTEXT, record = RecordState(true, 78, "t0")), events = emptyList(),
            ineligible = IneligibleReason.PACE_BAND)
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        val d = assertNotNull(p.state.value.detail)
        val s = assertNotNull(d.score)
        assertEquals("−3 vs your usual highway drives", s.comparison?.text)
        assertEquals(ComparisonTone.LOWER, s.comparison?.tone)
        assertEquals("New route record · beat 78 from 14 Oct", s.recordLine)
        assertEquals("Unusual pace — not counted for records", s.paceNote)
        assertTrue(s.accessibilityLabel.endsWith("Unusual pace — not counted for records."))
        assertNull(s.basedOn)
        assertEquals("Compared with your usual highway drives: 83.", d.breakdown?.basisLine)
        assertEquals("Its route doesn’t have three earlier runs yet", d.breakdown?.options?.first()?.detail)
        assertEquals("The Commute", d.breakdown?.options?.last()?.title)
        val events = assertNotNull(d.events)
        assertTrue(events.rows.isEmpty())
        assertEquals("No abrupt inputs on this drive.", events.emptyTitle)
    }

    @Test
    fun `calibrating reads your baseline on the first scored drive, then k of 3, with no bands`() = runTest(UnconfinedTestDispatcher()) {
        val first = trip("t1", startedAt = toronto(2026, 11, 1, 8))
        val second = trip("t2", startedAt = toronto(2026, 11, 2, 8))
        trips.value = listOf(second, first)
        reader.lines.value = ScoreLines(
            mapOf("t1" to ScoreLine("t1", 76, null, Calibrating(1, 3), false, null, null), "t2" to ScoreLine("t2", 78, null,
                Calibrating(2, 3), false, null, null)),
            0,
        )
        scored(first, score("t1", 76, null, Calibrating(1, 3)))
        scored(second, score("t2", 78, null, Calibrating(2, 3)))
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t1"))
        val c1 = assertNotNull(p.state.value.detail?.score?.comparison)
        assertEquals("Your baseline. Every drive from here is compared to you, not to anyone else.", c1.text)
        p.dispatch(DrivesIntent.Open("t2"))
        val s2 = assertNotNull(p.state.value.detail?.score)
        assertEquals("Calibrating your baseline · 2 of 3", s2.comparison?.text)
        assertEquals(ComparisonTone.CALIBRATING, s2.comparison?.tone)
        assertEquals(2, s2.comparison?.calibratingK)
        assertNull(s2.baseline)
        assertTrue(s2.contributors.all { it.band == null && it.bandText == null })
        assertEquals("2 abrupt stops", s2.contributors[0].reason)
        val b = assertNotNull(p.state.value.detail?.breakdown)
        assertTrue(b.basisLine.startsWith("Calibrating your baseline · 2 of 3. We compare each drive"))
        assertTrue(b.options.isEmpty())
    }

    @Test
    fun `passenger and not-a-drive trips hide every score`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(kind = TripKind.PASSENGER, quality = quality(QualityBadge.GOOD))
        trips.value = listOf(t)
        // A withheld row (D's hand-over): smoothness null, other columns kept.
        scored(t, score(smoothness = null))
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        val d = assertNotNull(p.state.value.detail)
        assertTrue(d.scoresHidden)
        assertNull(d.score)
        assertNull(d.events)
        assertNull(d.tip)
        assertTrue(d.meta!!.passenger)
        assertTrue(d.banners.any { it.text.startsWith("Marked as passenger") })
        assertTrue(analytics.named("score_viewed").isEmpty())
    }

    @Test
    fun `an unscorable drive says why and has no tip`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(quality = quality(QualityBadge.UNSCORABLE, QualityReason.NOT_ALIGNED)).copy(rawState = RawState.SUMMARY_ONLY)
        trips.value = listOf(t)
        scored(t, score(smoothness = null), events = emptyList())
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        val d = assertNotNull(p.state.value.detail)
        val s = assertNotNull(d.score)
        assertNull(s.smoothness)
        assertFalse(s.isScored)
        assertEquals("We couldn’t work out which way your phone faced.", s.notScoredReason)
        assertTrue(s.contributors.isEmpty())
        assertNull(d.tip)
        assertNull(d.cleanest)
        assertEquals("Events aren’t detected when the phone moves around freely.", d.events?.note)
        assertFalse(d.canAnalyse)
        assertTrue(d.breakdown!!.notScoredText!!.startsWith("We couldn’t work out which way your phone faced."))
        assertEquals("Unscored drives don’t count toward your baseline, routes or weekly average.", d.breakdown.notScoredFooter)
    }

    @Test
    fun `navigation to the score screens and their analytics`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(startedAt = toronto(2026, 11, 8, 8))
        trips.value = listOf(t)
        scored(t, score())
        val p = presenter()
        p.dispatch(DrivesIntent.OpenTrip("t"))
        assertEquals(DrivesEffect.OpenTrip("t"), p.effects.first())
        p.dispatch(DrivesIntent.Open("t"))
        assertEquals(listOf(mapOf("source" to "drives", "age_bucket" to "1_7d")), analytics.named("trip_detail_opened"))
        assertEquals(listOf(mapOf("surface" to "trip_detail")), analytics.named("score_viewed"))
        p.dispatch(DrivesIntent.OpenBreakdown("t"))
        assertEquals(DrivesEffect.OpenBreakdown("t"), p.effects.first())
        p.dispatch(DrivesIntent.ShowBreakdown("t"))
        assertEquals(listOf("trip_detail", "breakdown"), analytics.named("score_viewed").map { it.getValue("surface") })
        assertEquals(1, analytics.named("trip_detail_opened").size)
        p.dispatch(DrivesIntent.OpenHowScoresWork(ExplainerSource.BREAKDOWN))
        assertEquals(DrivesEffect.OpenHowScoresWork, p.effects.first())
        assertEquals(listOf(mapOf("source" to "breakdown")), analytics.named("scores_explainer_viewed"))
        p.dispatch(DrivesIntent.TapContributor(ContributorId.CORNERING))
        assertEquals(listOf(mapOf("contributor" to "cornering")), analytics.named("contributor_tapped"))
        p.dispatch(DrivesIntent.OpenAnalysis("t"))
        assertEquals(DrivesEffect.OpenAnalysis("t", "Sunday morning"), p.effects.first())
    }

    @Test
    fun `a trip opened before the list arrives is recorded once it is listed`() = runTest(UnconfinedTestDispatcher()) {
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        assertTrue(analytics.named("trip_detail_opened").isEmpty())
        val t = trip(startedAt = toronto(2026, 11, 10, 12))
        scored(t, score())
        trips.value = listOf(t)
        assertEquals(listOf(mapOf("source" to "drives", "age_bucket" to "lt_1d")), analytics.named("trip_detail_opened"))
        assertEquals(1, analytics.named("score_viewed").size)
        assertEquals(listOf("t"), reader.opened)
    }

    @Test
    fun `the explainer is always in the state`() = runTest(UnconfinedTestDispatcher()) {
        val explainer = assertNotNull(presenter().state.value.explainer)
        assertEquals("How scores work", explainer.title)
        assertEquals("Scoring version smooth-1.0.0 · updated November 10, 2026", explainer.versionLine)
        assertTrue(explainer.sections.any { it.kind == ExplainerSectionKind.EVENTS })
    }

    @Test
    fun `a thousand drives render in well under a second`() = runTest(UnconfinedTestDispatcher()) {
        val many = (0 until 1_000).map { trip("t$it", startedAt = toronto(2026, 11, 10, 8) - it * 3_600_000L,
            quality = quality(QualityBadge.GOOD)) }
        reader.lines.value = ScoreLines(many.associate { it.id to ScoreLine(it.id, 70 + it.id.length, 1, null, false, route, "GTI") }, 2)
        val p = presenter()
        trips.value = many.drop(1)
        // DRIVE-126 AC2: building every card and week, warm.
        val start = System.nanoTime()
        trips.value = many
        val ms = (System.nanoTime() - start) / 1_000_000
        assertEquals(1_000, p.state.value.trips.size)
        assertEquals(1_000, p.state.value.sections.sumOf { it.rows.size })
        assertTrue(ms < 1_000, "1,000 drives rendered in $ms ms")
    }

    @Test
    fun `trip age buckets`() {
        val day = 86_400_000L
        assertEquals(listOf("lt_1d", "1_7d", "1_7d", "8_30d", "8_30d", "gt_30d"), listOf(0L, day, 7 * day, 8 * day, 30 * day,
            31 * day).map(::ageBucket))
    }

    @Test
    fun `the manual source and the event tags`() = runTest(UnconfinedTestDispatcher()) {
        val t = trip(source = TripSource.MANUAL, quality = quality(QualityBadge.GOOD))
        trips.value = listOf(t)
        scored(t, score(), feedback = mapOf("e1" to EventFeedback("e1", "t", false,
            com.driveperf.domain.trip.InaccurateReason.PHONE_MOVED, 1L)))
        val p = presenter()
        p.dispatch(DrivesIntent.Open("t"))
        val d = assertNotNull(p.state.value.detail)
        assertEquals("Started by you", d.meta?.source)
        val rows = d.events!!.rows.associateBy { it.id }
        assertEquals("Not counted", rows.getValue("e1").tag)
        assertTrue(rows.getValue("e1").excluded)
        assertEquals("Low confidence", rows.getValue("e2").tag)
        assertNull(rows.getValue("e3").tag)
        assertEquals("Hard cornering at ${JavaTimeFormatter().time(t.startedAt + 300_000)}. Peak 0.40 g over 2.0 seconds.",
            rows.getValue("e3").accessibilityLabel)
    }
}
