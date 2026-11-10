package com.driveperf.feature.garage

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.driveperf.designsystem.DpEmptyState

/** The Garage tab root. Its content arrives with the Garage feature tickets; until then it says so plainly. */
@Composable
fun GarageScreen(modifier: Modifier = Modifier) {
    DpEmptyState(
        title = stringResource(R.string.garage_empty_title),
        body = stringResource(R.string.garage_empty_body),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    )
}
