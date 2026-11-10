package com.driveperf.feature.routes

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.driveperf.designsystem.DpEmptyState

/** The Routes tab root. Its content arrives with the Routes feature tickets; until then it says so plainly. */
@Composable
fun RoutesScreen(modifier: Modifier = Modifier) {
    DpEmptyState(
        title = stringResource(R.string.routes_empty_title),
        body = stringResource(R.string.routes_empty_body),
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
    )
}
