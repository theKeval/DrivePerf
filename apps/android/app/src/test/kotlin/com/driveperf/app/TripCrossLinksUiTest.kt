package com.driveperf.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.score.Band
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.feature.drives.ScoreBreakdownScreen
import com.driveperf.feature.drives.TIP_CARD_TAG
import com.driveperf.feature.drives.TRIP_MAP_TAG
import com.driveperf.feature.drives.TripDetailScreen
import com.driveperf.presentation.analysis.MapPin
import com.driveperf.presentation.analysis.MapPoint
import com.driveperf.presentation.analysis.MapSegment
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesState
import com.driveperf.presentation.drives.TripDetailUi
import com.driveperf.presentation.drives.TripEventsUi
import com.driveperf.presentation.drives.TripMapUi
import com.driveperf.presentation.score.BreakdownUi
import com.driveperf.presentation.score.ComparisonTone
import com.driveperf.presentation.score.ComparisonUi
import com.driveperf.presentation.score.ContributorUi
import com.driveperf.presentation.score.EventCountUi
import com.driveperf.presentation.score.EventRowUi
import com.driveperf.presentation.score.ScoreUi
import com.driveperf.presentation.score.TipUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DRIVE-87 AC2, DRIVE-55, DRIVE-102: trip detail's map hero, event rows, contributors and tip card, and S-SCO-01's
 * contributor events, each hand the right intent to the shared presenter.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class TripCrossLinksUiTest {
    @get:Rule val compose = createComposeRule()

    private val actions = mutableListOf<DrivesIntent>()

    private val pinned = EventRowUi("e1", EventType.BRAKE, "Hard braking", "8:14 AM", "0.42 g · 1.4 s",
        accessibilityLabel = "Hard braking at 8:14 AM.")

    /** No position (a GPS gap): no pin on the map, but its row still opens the evidence. */
    private val unpinned = EventRowUi("e2", EventType.ACCEL, "Hard acceleration", "8:20 AM", "0.31 g · 2.0 s",
        accessibilityLabel = "Hard acceleration at 8:20 AM.")
    private val excluded = EventRowUi("e3", EventType.CORNER, "Hard cornering", "8:25 AM", "0.40 g · 2.0 s", excluded = true,
        tag = "Not counted", accessibilityLabel = "Hard cornering at 8:25 AM. Not counted.")
    private val contributorEvent = EventRowUi("e4", EventType.BRAKE, "Hard braking", "8:30 AM", "0.35 g · 1.1 s",
        accessibilityLabel = "Hard braking at 8:30 AM.")
    private val contributors = listOf(
        ContributorUi(ContributorId.BRAKING, "Braking", Band.ROUGHER, "▼", "Rougher than usual", "1 abrupt stop",
            listOf(contributorEvent), "Braking, rougher than usual. 1 abrupt stop. Show events."),
        ContributorUi(ContributorId.CONSISTENCY, "Consistency", Band.USUAL, "●", "Usual", "Your usual rhythm", emptyList(),
            "Consistency, usual. Your usual rhythm."),
    )
    private val score = ScoreUi(85, 83, ComparisonUi(ComparisonTone.BETTER, "▲", "+2 vs your usual"), null, null, null, null,
        contributors, "Smoothness 85. 2 above your usual.")
    private val breakdown = BreakdownUi("Compared with your usual: 83.", emptyList(), "", null, emptyList(), null,
        "Each contributor is compared with your usual.", null, null)
    private val map = TripMapUi(
        widthM = 400.0, heightM = 800.0,
        segments = listOf(
            MapSegment(SegmentStyle.ESTIMATED, doubleArrayOf(0.0, 100.0), doubleArrayOf(0.0, 200.0)),
            MapSegment(SegmentStyle.OBSERVED, doubleArrayOf(100.0, 400.0), doubleArrayOf(200.0, 800.0)),
        ),
        pins = listOf(MapPin("e1", EventType.BRAKE, false, MapPoint(200.0, 400.0))),
        stops = listOf(MapPoint(300.0, 600.0)),
        maxSpeed = MapPoint(350.0, 700.0),
        estimatedStart = true,
        accessibilityLabel = "Map of your drive, 39.0 km. 1 event, 1 stop.",
    )
    private val tip = TipUi("brake_light", TipCategory.BRAKING, "One thing for next time", "Ease off earlier",
        "Next time, ease off a little earlier.", "Why.", eventId = "e1")
    private val detail = TripDetailUi(
        "trip-7", "The Commute", emptyList(), emptyList(), QualityBadge.GOOD, TripCorrection.DRIVER, "Tuesday morning · 10 Nov",
        score = score, breakdown = breakdown,
        events = TripEventsUi(
            "Events · 3", listOf(EventCountUi(EventType.BRAKE, "Braking", 1)), listOf(pinned, unpinned, excluded), "", "",
        ),
        tip = tip, map = map,
    )

    private fun show(detail: TripDetailUi = this.detail) {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                TripDetailScreen(DrivesState(loaded = true, detail = detail), "trip-7", onAction = { actions += it })
            }
        }
        compose.waitForIdle()
    }

    @Test fun `the map hero reads as one labelled button that opens Replay`() {
        show()
        compose.onNodeWithTag(TRIP_MAP_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Map of your drive, 39.0 km. 1 event, 1 stop.").performClick()
        assertEquals(listOf(DrivesIntent.OpenReplay("trip-7")), actions)
    }

    @Test fun `no route, no map hero`() {
        show(detail.copy(map = null))
        compose.onNodeWithTag(TRIP_MAP_TAG).assertDoesNotExist()
    }

    @Test fun `event rows open the evidence, pinned or not, but an excluded one opens nothing`() {
        show()
        compose.onNodeWithContentDescription("Hard acceleration at 8:20 AM.").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Hard cornering at 8:25 AM. Not counted.").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Hard braking at 8:14 AM.").performScrollTo().performClick()
        assertEquals(listOf(DrivesIntent.OpenEvidence("trip-7", "e2"), DrivesIntent.OpenEvidence("trip-7", "e1")), actions)
    }

    @Test fun `a contributor on trip detail lists its events, and each opens the evidence`() {
        show()
        compose.onNodeWithContentDescription("Hard braking at 8:30 AM.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Braking, rougher than usual. 1 abrupt stop. Show events.").performClick()
        compose.onNodeWithContentDescription("Hard braking at 8:30 AM.").performScrollTo().performClick()
        // A contributor without events has nothing to open.
        compose.onNodeWithContentDescription("Consistency, usual. Your usual rhythm.").performClick()
        assertEquals(listOf(DrivesIntent.TapContributor(ContributorId.BRAKING), DrivesIntent.OpenEvidence("trip-7", "e4")), actions)
    }

    @Test fun `the tip card opens S-SCO-04 and See the moment opens its evidence`() {
        show()
        compose.onNodeWithTag(TIP_CARD_TAG).performScrollTo().performClick()
        compose.onNodeWithText("See the moment ›").performScrollTo().performClick()
        assertEquals(listOf(DrivesIntent.OpenTip("trip-7"), DrivesIntent.OpenTipEvidence("trip-7")), actions)
    }

    @Test fun `a tip with no moment has no See the moment`() {
        show(detail.copy(tip = tip.copy(eventId = null)))
        compose.onNodeWithTag(TIP_CARD_TAG).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("See the moment ›").assertDoesNotExist()
    }

    @Test fun `the breakdown's open contributor lists its events, which open the evidence`() {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                ScoreBreakdownScreen(DrivesState(loaded = true, detail = detail), "trip-7", onAction = { actions += it })
            }
        }
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("Hard braking at 8:30 AM.")[0].performScrollTo().performClick()
        assertEquals(listOf(DrivesIntent.OpenEvidence("trip-7", "e4")), actions)
    }
}
