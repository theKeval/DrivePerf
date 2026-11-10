package com.driveperf.feature.insights

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.driveperf.designsystem.DpEmptyState

/** The Insights tab root. Its content arrives with the Insights feature tickets; until then it says so plainly. */
@Composable
fun InsightsScreen(modifier: Modifier = Modifier) {
    DpEmptyState(
        title = stringResource(R.string.insights_empty_title),
        body = stringResource(R.string.insights_empty_body),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    )
}
