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
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.feature.drives.AllDrivesScreen
import com.driveperf.feature.drives.DRIVES_FILTERS_SHOW_TAG
import com.driveperf.feature.drives.DRIVES_FILTERS_TAG
import com.driveperf.feature.drives.TRIP_EDIT_BUTTON_TAG
import com.driveperf.feature.drives.TRIP_EDIT_DELETE_TAG
import com.driveperf.feature.drives.TRIP_EDIT_SAVE_TAG
import com.driveperf.feature.drives.TripDetailScreen
import com.driveperf.presentation.drives.CarOptionUi
import com.driveperf.presentation.drives.DeleteTripUi
import com.driveperf.presentation.drives.DrivesIntent
import com.driveperf.presentation.drives.DrivesState
import com.driveperf.presentation.drives.FilterChipUi
import com.driveperf.presentation.drives.FilterGroup
import com.driveperf.presentation.drives.FilterGroupUi
import com.driveperf.presentation.drives.FilterKey
import com.driveperf.presentation.drives.FilterSheetUi
import com.driveperf.presentation.drives.HistoryFilterAction
import com.driveperf.presentation.drives.HistoryFilterUi
import com.driveperf.presentation.drives.TripDetailUi
import com.driveperf.presentation.drives.TripEditAction
import com.driveperf.presentation.drives.TripEditMessageUi
import com.driveperf.presentation.drives.TripEditPage
import com.driveperf.presentation.drives.TripEditType
import com.driveperf.presentation.drives.TripEditUi
import com.driveperf.presentation.drives.TripRowUi
import com.driveperf.presentation.drives.TripSectionUi
import com.driveperf.presentation.drives.TripTypeOptionUi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** DRIVE-131 S-TRP-02 Edit trip and S-TRP-03 Delete trip, and DRIVE-126 AC3 the All drives filters. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class TripEditUiTest {
    @get:Rule val compose = createComposeRule()

    private val actions = mutableListOf<DrivesIntent>()

    private val detail = TripDetailUi("trip-7", "The Commute", emptyList(), emptyList(), null, TripCorrection.DRIVER, "Tuesday morning · 10 Nov")

    private fun type(type: TripEditType, title: String, detail: String, selected: Boolean = false, current: Boolean = false) =
        TripTypeOptionUi(type, title, detail, selected, current, "$title. $detail.")

    private val edit = TripEditUi(
        tripId = "trip-7", page = TripEditPage.MAIN, title = "Edit trip", tripTitle = "The Commute",
        tripSubtitle = "Tuesday morning · 10 Nov", tripStats = "8.1 km · 12 min", score = null, type = TripEditType.DRIVER,
        typeValue = "Driver", typeDetail = "Scored",
        types = listOf(
            type(TripEditType.DRIVER, "Driver", "Scored and counted toward your baseline, routes and recaps.", true, true),
            type(TripEditType.PASSENGER, "Passenger", "Not scored. Stays in your history."),
            type(TripEditType.TAXI, "Taxi or rideshare", "Not scored. Stays in your history."),
        ),
        typeBanner = null, typeFooter = "Correcting a drive teaches DrivePerf what your drives look like.",
        carValue = "GTI", carDetail = "2019 Volkswagen Golf GTI",
        cars = listOf(
            CarOptionUi("gti", "GTI", "2019 Volkswagen Golf GTI", isDefault = true, selected = true, current = true, "GTI. Selected."),
            CarOptionUi("ioniq", "Ioniq", "2023 Hyundai Ioniq 5", isDefault = false, selected = false, current = false, "Ioniq. Not selected."),
        ),
        carNote = "Drives go to your default car unless you change them here or on the Story.",
        changedBanner = null, changed = false, caption = "Fix a drive after the fact.", deleteLabel = "Delete drive",
    )

    private fun showDetail(state: DrivesState) {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) { TripDetailScreen(state, "trip-7", onAction = { actions += it }) }
        }
        compose.waitForIdle()
    }

    private fun DrivesIntent.edit() = (this as DrivesIntent.Edit).action

    @Test fun `Edit on trip detail opens the edit sheet, and only when editing is wired`() {
        showDetail(DrivesState(loaded = true, detail = detail, canEdit = true))
        compose.onNodeWithTag(TRIP_EDIT_BUTTON_TAG).assertIsDisplayed().performClick()
        assertEquals(listOf<DrivesIntent>(DrivesIntent.Edit(TripEditAction.Open("trip-7", TripEditPage.MAIN))), actions)
    }

    @Test fun `without the edit ports there is no Edit button`() {
        showDetail(DrivesState(loaded = true, detail = detail))
        compose.onNodeWithTag(TRIP_EDIT_BUTTON_TAG).assertDoesNotExist()
    }

    @Test fun `the edit sheet shows the type and car and sends its actions`() {
        showDetail(DrivesState(loaded = true, detail = detail, canEdit = true, edit = edit))
        compose.onNodeWithText("Edit trip").assertIsDisplayed()
        compose.onNodeWithContentDescription("Trip type, Driver, Scored").performClick()
        compose.onNodeWithContentDescription("Car, GTI, 2019 Volkswagen Golf GTI").performClick()
        compose.onNodeWithTag(TRIP_EDIT_DELETE_TAG).performScrollTo().performClick()
        compose.onNodeWithTag(TRIP_EDIT_SAVE_TAG).performClick()
        assertEquals(
            listOf(
                TripEditAction.ShowPage(TripEditPage.TYPE), TripEditAction.ShowPage(TripEditPage.CAR),
                TripEditAction.AskDelete("trip-7"), TripEditAction.Save,
            ),
            actions.map { it.edit() },
        )
    }

    @Test fun `the type picker selects a type and shows the banner`() {
        val picker = edit.copy(page = TripEditPage.TYPE, typeBanner = "Passenger: not a drive you made.")
        showDetail(DrivesState(loaded = true, detail = detail, canEdit = true, edit = picker))
        // Each row is one radio button read by its label ("Current type." comes from the presenter).
        compose.onNodeWithContentDescription("Driver. Scored and counted toward your baseline, routes and recaps..").assertIsDisplayed()
        compose.onNodeWithText("Passenger: not a drive you made.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Taxi or rideshare. Not scored. Stays in your history..").performClick()
        compose.onNodeWithContentDescription("Back to Edit trip").performClick()
        assertEquals(
            listOf(TripEditAction.PickType(TripEditType.TAXI), TripEditAction.ShowPage(TripEditPage.MAIN)),
            actions.map { it.edit() },
        )
    }

    @Test fun `the car picker moves the trip to another car`() {
        showDetail(DrivesState(loaded = true, detail = detail, canEdit = true, edit = edit.copy(page = TripEditPage.CAR)))
        compose.onNodeWithContentDescription("GTI. Selected.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Ioniq. Not selected.").performClick()
        assertEquals(listOf<TripEditAction>(TripEditAction.PickCar("ioniq")), actions.map { it.edit() })
    }

    @Test fun `delete asks first and waits while it runs`() {
        val dialog = DeleteTripUi("trip-7", "Delete this drive?", "This can’t be undone.", "The Commute", "Tuesday morning · 10 Nov",
            "Delete", "Cancel", deleting = false, error = "Couldn’t delete this drive. Try again.")
        showDetail(DrivesState(loaded = true, detail = detail, canEdit = true, deleting = dialog))
        compose.onNodeWithText("Delete this drive?").assertIsDisplayed()
        compose.onNodeWithText("Couldn’t delete this drive. Try again.").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf(TripEditAction.CancelDelete, TripEditAction.ConfirmDelete), actions.map { it.edit() })
    }

    @Test fun `a running delete disables both buttons`() {
        val dialog = DeleteTripUi("trip-7", "Delete this drive?", "This can’t be undone.", "The Commute", "Tuesday morning · 10 Nov",
            "Delete", "Cancel", deleting = true, error = null)
        showDetail(DrivesState(loaded = true, detail = detail, canEdit = true, deleting = dialog))
        compose.onNodeWithText("Delete").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
    }

    @Test fun `the saved toast offers Undo`() {
        val message = TripEditMessageUi("trip-7", "Marked as passenger · scores updated", "Undo")
        showDetail(DrivesState(loaded = true, detail = detail, canEdit = true, editMessage = message))
        compose.onNodeWithText("Marked as passenger · scores updated").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        assertEquals(listOf<TripEditAction>(TripEditAction.Undo), actions.map { it.edit() })
    }

    private val commute = FilterKey(FilterGroup.ROUTE, "r1")
    private val gti = FilterKey(FilterGroup.CAR, "gti")
    private val rows = listOf(TripRowUi("trip-7", "The Commute", "Tuesday morning · 10 Nov", "8.1 km", "12 min", accessibilityLabel = "The Commute card"))

    private fun showList(history: HistoryFilterUi) {
        compose.setContent {
            DrivePerfTheme(darkTheme = true) {
                AllDrivesScreen(
                    state = DrivesState(loaded = true, trips = rows, sections = listOf(TripSectionUi("w", "This week", "1 drive", rows)),
                        history = history),
                    onAction = { actions += it },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun DrivesIntent.history() = (this as DrivesIntent.History).action

    @Test fun `applied filters show as chips that remove themselves, with Clear and quick filters`() {
        showList(
            HistoryFilterUi(
                activeCount = 1, buttonAccessibilityLabel = "Filters, 1 applied",
                applied = listOf(FilterChipUi(commute, "The Commute", 1, true, accessibilityLabel = "Remove filter The Commute")),
                suggestions = listOf(FilterChipUi(gti, "GTI", 1, false, accessibilityLabel = "GTI, 1 drive")),
                subtitle = "1 drive · filtered",
            ),
        )
        compose.onNodeWithText("1 drive · filtered").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filters, 1 applied").performClick()
        compose.onNodeWithContentDescription("Remove filter The Commute").performClick()
        compose.onNodeWithContentDescription("GTI, 1 drive").performClick()
        compose.onNodeWithText("Clear").performClick()
        assertEquals(
            listOf(HistoryFilterAction.OpenSheet, HistoryFilterAction.Toggle(commute), HistoryFilterAction.Toggle(gti),
                HistoryFilterAction.Clear),
            actions.map { it.history() },
        )
    }

    @Test fun `the filter sheet toggles chips, resets and shows the matching drives`() {
        val sheet = FilterSheetUi(
            "Filter drives", "1 filter · 3 drives",
            listOf(
                FilterGroupUi(FilterGroup.CAR, "Car", listOf(FilterChipUi(gti, "GTI", 3, true, accessibilityLabel = "GTI, 3 drives, selected"))),
                FilterGroupUi(FilterGroup.QUALITY, "Data quality", listOf(
                    FilterChipUi(FilterKey.quality(QualityBadge.GOOD), "Good", 2, false, QualityBadge.GOOD, "Good, 2 drives, not selected"),
                )),
            ),
            "Show 3 drives", canShow = true, "Reset", canReset = true,
        )
        showList(HistoryFilterUi(sheet = sheet))
        compose.onNodeWithText("Filter drives").assertIsDisplayed()
        compose.onNodeWithContentDescription("Good, 2 drives, not selected").performClick()
        compose.onNodeWithText("Reset").performClick()
        compose.onNodeWithTag(DRIVES_FILTERS_SHOW_TAG).performClick()
        assertEquals(
            listOf(HistoryFilterAction.ToggleDraft(FilterKey.quality(QualityBadge.GOOD)), HistoryFilterAction.ResetDraft,
                HistoryFilterAction.Apply),
            actions.map { it.history() },
        )
    }

    @Test fun `with no match the sheet cannot show and the list offers to clear`() {
        val sheet = FilterSheetUi("Filter drives", "1 filter · 0 drives", emptyList(), "No drives match", canShow = false, "Reset", true)
        showList(HistoryFilterUi(activeCount = 1, emptyTitle = "No drives match.", emptyBody = "Try fewer filters.", sheet = sheet))
        compose.onNodeWithTag(DRIVES_FILTERS_SHOW_TAG).assertIsNotEnabled()
        compose.onNodeWithTag(DRIVES_FILTERS_TAG).assertIsDisplayed()
    }
}
