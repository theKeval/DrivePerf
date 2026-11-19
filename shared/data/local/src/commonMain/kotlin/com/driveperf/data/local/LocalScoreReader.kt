package com.driveperf.data.local

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.driveperf.data.local.db.DrivePerfDatabase
import com.driveperf.data.local.db.ScoreLines
import com.driveperf.data.local.db.TripLabels
import com.driveperf.domain.route.IneligibleReason
import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.score.Calibrating
import com.driveperf.domain.score.RouteName
import com.driveperf.domain.score.ScoreLine
import com.driveperf.domain.score.ScoreReader
import com.driveperf.domain.score.TripScoreDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import com.driveperf.domain.score.ScoreLines as DomainScoreLines

/** [ScoreReader] on SQLDelight (`ScoreList.sq`). Every query runs on [io]. */
public class LocalScoreReader(private val db: DrivePerfDatabase, private val io: CoroutineContext) : ScoreReader {
    private val q get() = db.scoreListQueries
    private val trips get() = db.tripQueries

    override fun observeLines(): Flow<DomainScoreLines> =
        combine(q.scoreLines().asFlow().mapToList(io), q.activeCarCount().asFlow().mapToOneOrNull(io)) { rows, cars ->
            DomainScoreLines(rows.associate { it.trip_id to it.toLine() }, cars?.toInt() ?: 0)
        }

    override fun observeDetail(tripId: String): Flow<TripScoreDetail> = combine(
        trips.scoreForTrip(tripId).asFlow().mapToOneOrNull(io),
        trips.eventsForTrip(tripId).asFlow().mapToList(io),
        trips.feedbackForTrip(tripId).asFlow().mapToList(io),
    ) { score, events, feedback ->
        // The labels and the runs change with the score rows (a route match is written with the score), so they are
        // read again whenever the score flow emits.
        val (labels, runs) = withContext(io) {
            val labels = q.tripLabels(tripId).executeAsOneOrNull()
            val runs = labels?.route_id?.let { route ->
                q.routeRuns(route, labels.started_at, tripId).executeAsList().map { it.toInt() }.reversed()
            }.orEmpty()
            labels to runs
        }
        TripScoreDetail(
            tripId = tripId,
            score = score?.toDomain(),
            events = events.map { it.toDomain() },
            feedback = feedback.map { it.toDomain() }.associateBy { it.eventId },
            route = labels?.route(),
            recordIneligible = labels?.ineligible_reason?.let { IneligibleReason.valueOf(it) },
            car = labels?.let { carName(it.car_nickname, it.car_make, it.car_model) },
            runs = runs,
        )
    }
}

private fun ScoreLines.toLine(): ScoreLine = ScoreLine(
    tripId = trip_id,
    smoothness = smoothness?.toInt(),
    delta = delta?.toInt(),
    calibrating = calibrating_k?.let { Calibrating(it.toInt(), calibrating_of?.toInt() ?: CALIBRATION_TRIPS) },
    newRecord = record_state_json?.let { columnJson.decodeFromString<RecordStateJson>(it).isNew } ?: false,
    route = route_id?.let { RouteName(it, route_name, route_preset?.let(RoutePreset::valueOf)) },
    car = carName(car_nickname, car_make, car_model),
)

private fun TripLabels.route(): RouteName? = route_id?.let { RouteName(it, route_name, route_preset?.let(RoutePreset::valueOf)) }

/** The nickname, else "Make Model"; null without either. */
private fun carName(nickname: String?, make: String?, model: String?): String? =
    nickname?.takeIf { it.isNotBlank() } ?: listOfNotNull(make, model).joinToString(" ").takeIf { it.isNotBlank() }

private const val CALIBRATION_TRIPS = 3
