package com.driveperf.designsystem

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Where every modal sheet can settle: fully expanded or hidden, with no partially expanded stop. With that stop, a tap on
 * the scrim while a swipe was still settling the sheet could leave M3's ModalBottomSheet stuck "animating": it then took
 * every touch as a drag and no control in it responded (OnePlus 7T, DRIVE-90 / DRIVE-215).
 */
@OptIn(ExperimentalMaterial3Api::class)
public val DpSheetValues: Set<SheetValue> = setOf(SheetValue.Hidden, SheetValue.Expanded)

/** A sheet state that opens fully expanded (no half-height stop); see [DpSheetValues]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun rememberDpSheetState(): SheetState =
    rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = DpSheetValues)

/**
 * The app's one ModalBottomSheet: opens fully expanded and cannot rest half-open, so the scrim-tap-while-settling freeze
 * cannot happen. Use this instead of ModalBottomSheet everywhere.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun DpModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberDpSheetState(),
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = containerColor,
        content = content,
    )
}
