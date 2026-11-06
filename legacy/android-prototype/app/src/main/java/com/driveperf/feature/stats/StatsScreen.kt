package com.driveperf.feature.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.driveperf.R
import com.driveperf.domain.model.DistanceUnit
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val LoadingSummaryCardHeight = 140.dp
private val LoadingChartCardHeight = 220.dp
private val LoadingRecordsCardHeight = 180.dp
private const val MinKnownBarHeightFraction = 0.03f
private const val MissingDataBarHeightFraction = 0.05f

@Composable
fun StatsScreen(
    onStartTripClick: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    StatsScreenContent(
        state = state,
        onStartTripClick = onStartTripClick,
    )
}

@Composable
private fun StatsScreenContent(
    state: StatsUiState,
    onStartTripClick: () -> Unit,
) {
    val locale = Locale.getDefault()
    val distanceUnit = state.distanceUnit
    var showGlossary by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.stats_title))
                        Text(
                            text = stringResource(R.string.stats_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showGlossary = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = stringResource(R.string.stats_info_content_description),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> StatsLoadingState(modifier = Modifier.padding(padding))
            !state.hasAnyTrips -> StatsEmptyState(
                modifier = Modifier.padding(padding),
                onStartTripClick = onStartTripClick,
            )

            else -> StatsPopulatedState(
                modifier = Modifier.padding(padding),
                state = state,
                locale = locale,
                distanceUnit = distanceUnit,
            )
        }
    }
    if (showGlossary) {
        AlertDialog(
            onDismissRequest = { showGlossary = false },
            confirmButton = {
                TextButton(onClick = { showGlossary = false }) {
                    Text(text = stringResource(R.string.stats_glossary_dismiss))
                }
            },
            title = { Text(text = stringResource(R.string.stats_glossary_title)) },
            text = { Text(text = stringResource(R.string.stats_glossary_body)) },
        )
    }
}

@Composable
private fun StatsLoadingState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LoadingCard(Modifier.weight(1f).height(LoadingSummaryCardHeight))
                LoadingCard(Modifier.weight(1f).height(LoadingSummaryCardHeight))
            }
        }
        LoadingCard(Modifier.fillMaxWidth().height(LoadingChartCardHeight))
        LoadingCard(Modifier.fillMaxWidth().height(LoadingRecordsCardHeight))
    }
}

@Composable
private fun LoadingCard(modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {}
}

@Composable
private fun StatsEmptyState(
    modifier: Modifier = Modifier,
    onStartTripClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.QueryStats,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.height(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.stats_no_trips_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.stats_no_trips_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStartTripClick) {
            Text(text = stringResource(R.string.stats_start_trip))
        }
    }
}

@Composable
private fun StatsPopulatedState(
    state: StatsUiState,
    locale: Locale,
    distanceUnit: DistanceUnit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.hasPartialData) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.stats_partial_data_banner),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Text(text = stringResource(R.string.stats_overview), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Route,
                label = stringResource(R.string.stats_total_distance),
                value = state.summary.formatTotalDistance(distanceUnit, locale),
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.QueryStats,
                label = stringResource(R.string.stats_total_trips),
                value = state.summary.formatTotalTrips(locale),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Timelapse,
                label = stringResource(R.string.stats_total_time),
                value = state.summary.formatTotalDuration(),
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Speed,
                label = stringResource(R.string.stats_average_speed),
                value = state.summary.formatAverageSpeed(
                    distanceUnit = distanceUnit,
                    locale = locale,
                    placeholder = stringResource(R.string.stats_no_data_short),
                ),
            )
        }

        LastSevenDaysChart(
            bars = state.chartBars,
            distanceUnit = distanceUnit,
            locale = locale,
        )

        Text(text = stringResource(R.string.stats_personal_records), style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RecordCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.EmojiEvents,
                label = stringResource(R.string.stats_record_longest_trip),
                record = state.longestTripRecord,
                type = RecordType.LONGEST_TRIP,
                distanceUnit = distanceUnit,
                locale = locale,
            )
            RecordCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Bolt,
                label = stringResource(R.string.stats_record_fastest_speed),
                record = state.fastestTripRecord,
                type = RecordType.FASTEST_AVERAGE_SPEED,
                distanceUnit = distanceUnit,
                locale = locale,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LastSevenDaysChart(
    bars: List<ChartBarStat>,
    distanceUnit: DistanceUnit,
    locale: Locale,
) {
    val knownDistances = bars.mapNotNull { it.distanceMeters }
    val maxDistance = knownDistances.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.stats_last_seven_days),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                bars.forEach { bar ->
                    val label = if (bar.isToday) {
                        stringResource(R.string.stats_today)
                    } else {
                        bar.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = bar.distanceMeters?.let { formatChartDistance(it, distanceUnit, locale) }
                                ?: stringResource(R.string.stats_no_data_short),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(
                            modifier = Modifier.height(88.dp),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val barHeightFraction = bar.distanceMeters?.let {
                                // Keep a small minimum bar height so very small values stay visible.
                                ((it / maxDistance).toFloat()).coerceIn(MinKnownBarHeightFraction, 1f)
                            } ?: MissingDataBarHeightFraction
                            val barColor = if (bar.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
                            val barModifier = Modifier
                                .fillMaxWidth()
                                .height((88 * barHeightFraction).roundToInt().dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            if (bar.distanceMeters == null) {
                                Box(
                                    modifier = barModifier
                                        .background(Color.Transparent)
                                        .padding(horizontal = 2.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                    )
                                }
                            } else {
                                Box(modifier = barModifier.background(barColor))
                            }
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (bar.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    record: PersonalRecord?,
    type: RecordType,
    distanceUnit: DistanceUnit,
    locale: Locale,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (record == null) {
                Card(
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Text(
                        modifier = Modifier.padding(8.dp),
                        text = stringResource(R.string.stats_not_enough_data),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text(
                    text = record.formatValue(type, distanceUnit, locale),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = record.formatMeta(distanceUnit, locale),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun StatsScreenPreview() {
    StatsScreenContent(
        state = StatsUiState(
            isLoading = false,
            hasAnyTrips = true,
            summary = SummaryStats(
                totalDistanceMeters = 12_000.0,
                totalTrips = 12,
                totalDurationMs = 10_800_000L,
                averageSpeedMetersPerHour = 4_000.0,
            ),
            chartBars = (0..6).map { index ->
                ChartBarStat(
                    date = LocalDate.now().minusDays((6 - index).toLong()),
                    distanceMeters = if (index == 2) 0.0 else (index + 1) * 1_000.0,
                    isToday = index == 6,
                )
            },
            longestTripRecord = PersonalRecord(
                tripId = 1L,
                date = LocalDate.now().minusDays(2),
                distanceMeters = 42_000.0,
                durationMs = 3_600_000L,
                averageSpeedMetersPerHour = 42_000.0,
            ),
            fastestTripRecord = PersonalRecord(
                tripId = 2L,
                date = LocalDate.now().minusDays(3),
                distanceMeters = 30_000.0,
                durationMs = 1_800_000L,
                averageSpeedMetersPerHour = 60_000.0,
            ),
        ),
        onStartTripClick = {},
    )
}
