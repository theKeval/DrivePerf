package com.driveperf.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.score.Band
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.TipCategory
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.feature.drives.ANALYSIS_BUTTON_TAG
import com.driveperf.feature.drives.AllDrivesScreen
import com.driveperf.feature.drives.DrivesHomeScreen
import com.driveperf.feature.drives.HowScoresWorkScreen
import com.driveperf.feature.drives.REPLAY_BUTTON_TAG
import com.driveperf.feature.drives.ScoreBreakdownScreen
import com.driveperf.feature.drives.TripDetailScreen
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesState
import com.driveperf.presentation.drives.ExplainerSource
import com.driveperf.presentation.drives.TripDetailUi
import com.driveperf.presentation.drives.TripEventsUi
import com.driveperf.presentation.drives.TripRowUi
import com.driveperf.presentation.drives.TripSectionUi
import com.driveperf.presentation.score.BreakdownUi
import com.driveperf.presentation.score.ComparisonTone
import com.driveperf.presentation.score.ComparisonUi
import com.driveperf.presentation.score.ContributorUi
import com.driveperf.presentation.score.EventCountUi
import com.driveperf.presentation.score.EventRowUi
import com.driveperf.presentation.score.ScoreBadgeUi
import com.driveperf.presentation.score.ScoreExplainer
import com.driveperf.presentation.score.ScoreUi
import com.driveperf.presentation.score.TipUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DRIVE-55, DRIVE-87, DRIVE-113, DRIVE-126: trip detail as a push screen, the score screens and the trip cards. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class TripDetailUiTest {
    @get:Rule val compose = createComposeRule()

    private val actions = mutableListOf<DrivesIntent>()

    private val event = EventRowUi("e1", EventType.BRAKE, "Hard braking", "8:14 AM", "0.42 g · 1.4 s", accessibilityLabel = "Hard braking at 8:14 AM.")
    private val contributors = listOf(
        ContributorUi(ContributorId.BRAKING, "Braking", Band.ROUGHER, "▼", "Rougher than usual", "1 abrupt stop", listOf(event), "Braking, rougher than usual. 1 abrupt stop. Show events."),
        ContributorUi(ContributorId.CONSISTENCY, "Consistency", Band.USUAL, "●", "Usual", "Your usual rhythm", emptyList(), "Consistency, usual. Your usual rhythm."),
    )
    private val score = ScoreUi(
        85, 83, ComparisonUi(ComparisonTone.BETTER, "▲", "+2 vs your usual on The Commute"), null, null,
        "Unusual pace — not counted for records", null, contributors,
        "Smoothness 85. 2 above your usual on The Commute. Unusual pace — not counted for records.",
    )
    private val breakdown = BreakdownUi(
        "Compared with your usual on The Commute: 83.", emptyList(), "Every comparison is against your own drives. Never anyone else’s.",
        null, emptyList(), null, "Each contributor is compared with your usual.", null, null,
    )
    private val detail = TripDetailUi(
        "trip-7", "The Commute", emptyList(), emptyList(), null, TripCorrection.DRIVER, "Tuesday morning · 10 Nov",
        score = score, breakdown = breakdown,
        events = TripEventsUi("Events · 1", listOf(EventCountUi(EventType.BRAKE, "Braking", 1)), listOf(event), "", ""),
        cleanest = "9.5 km with no abrupt inputs.",
        tip = TipUi("brake_light", TipCategory.BRAKING, "One thing for next time", "Ease off earlier", "Next time, ease off a little earlier.", "Why."),
    )

    private fun show(detail: TripDetailUi = this.detail) {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                TripDetailScreen(DrivesState(loaded = true, detail = detail), "trip-7", onAction = { actions += it })
            }
        }
        compose.waitForIdle()
    }

    @Test fun `Replay and Analysis on trip detail hand the trip on`() {
        show()
        compose.onNodeWithTag(REPLAY_BUTTON_TAG).assertIsDisplayed().performClick()
        compose.onNodeWithTag(ANALYSIS_BUTTON_TAG).performClick()
        assertEquals(listOf(DrivesIntent.OpenReplay("trip-7"), DrivesIntent.OpenAnalysis("trip-7")), actions)
    }

    @Test fun `Analysis is off once the detailed data was removed`() {
        show(detail.copy(canAnalyse = false))
        compose.onNodeWithTag(ANALYSIS_BUTTON_TAG).assertIsNotEnabled()
    }

    @Test fun `the score card shows the comparison and contributors and opens the score screens`() {
        show()
        compose.onNodeWithContentDescription("Smoothness 85. 2 above your usual on The Commute.", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Braking, rougher than usual. 1 abrupt stop. Show events.").assertIsDisplayed()
        compose.onNodeWithText("How scores work").performClick()
        compose.onNodeWithText("Smoothness breakdown").performScrollTo().performClick()
        assertEquals(
            listOf(DrivesIntent.OpenHowScoresWork(ExplainerSource.TRIP_DETAIL), DrivesIntent.OpenBreakdown("trip-7")),
            actions,
        )
        compose.onNodeWithText("Events · 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Next time, ease off a little earlier.").performScrollTo().assertIsDisplayed()
    }

    @Test fun `a passenger trip shows no score, events or tip`() {
        show(detail.copy(scoresHidden = true))
        compose.onNodeWithText("Smoothness breakdown").assertDoesNotExist()
        compose.onNodeWithText("Events · 1").assertDoesNotExist()
        compose.onNodeWithText("Next time, ease off a little earlier.").assertDoesNotExist()
    }

    @Test fun `a trip card pushes trip detail`() {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                DrivesHomeScreen(
                    state = DrivesState(
                        loaded = true,
                        trips = listOf(
                            TripRowUi(
                                "trip-7", "The Commute", "Tuesday morning · 10 Nov", "8.1 km", "12 min", accessibilityLabel = "The Commute card",
                                score = ScoreBadgeUi("85", "+2", ComparisonTone.BETTER),
                            ),
                        ),
                    ),
                    onSeeAll = {},
                    onAction = { actions += it },
                )
            }
        }
        compose.onNodeWithContentDescription("The Commute card").performClick()
        assertEquals(listOf<DrivesIntent>(DrivesIntent.OpenTrip("trip-7")), actions)
    }

    @Test fun `all drives groups trips by week`() {
        val row = TripRowUi("trip-7", "The Commute", "Tuesday morning · 10 Nov", "8.1 km", "12 min")
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                AllDrivesScreen(
                    DrivesState(
                        loaded = true, trips = listOf(row),
                        sections = listOf(TripSectionUi("w", "This week", "1 drive · avg 85", listOf(row))),
                        sinceText = "That’s every drive since 10 November.",
                    ),
                )
            }
        }
        compose.onNodeWithContentDescription("This week, 1 drive · avg 85").assertIsDisplayed()
        compose.onNodeWithText("That’s every drive since 10 November.").assertIsDisplayed()
    }

    @Test fun `the breakdown expands a contributor's events and records the tap`() {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                ScoreBreakdownScreen(DrivesState(loaded = true, detail = detail), "trip-7", onAction = { actions += it })
            }
        }
        // The first contributor with events starts open.
        compose.onNodeWithContentDescription("Hard braking at 8:14 AM.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Braking, rougher than usual. 1 abrupt stop. Show events.").performClick()
        compose.onNodeWithContentDescription("Hard braking at 8:14 AM.").assertDoesNotExist()
        compose.onNodeWithContentDescription("Braking, rougher than usual. 1 abrupt stop. Show events.").performClick()
        assertEquals(listOf<DrivesIntent>(DrivesIntent.TapContributor(ContributorId.BRAKING)), actions)
        compose.onNodeWithContentDescription("Unusual pace — not counted for records", substring = true).assertIsDisplayed()
    }

    @Test fun `how scores work lists the version and date`() {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) { HowScoresWorkScreen(ScoreExplainer.content { "10 November 2026" }) }
        }
        compose.onNodeWithText("Scoring version smooth-1.0.0 · updated 10 November 2026").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Fair by design").performScrollTo().assertIsDisplayed()
    }
}
