package com.driveperf.pipeline.route

import com.driveperf.domain.repository.RouteHistory
import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.route.PersonalBest
import com.driveperf.domain.route.Route
import com.driveperf.domain.score.Score
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import kotlin.math.PI
import kotlin.math.cos

/** Synthetic paths in the ocean test box, built from metres east and north of an origin. */
internal object RouteTestData {
    private const val LAT0 = 2.0
    private const val LNG0 = -27.0
    private const val M_PER_DEG = 111_320.0

    fun row(east: Double, north: Double, t: Double, q: Int = 0): PreviewRow = PreviewRow(
        t, LAT0 + north / M_PER_DEG, LNG0 + east / (M_PER_DEG * cos(LAT0 * PI / 180)), 10.0, null, null, null, q,
    )

    /** An L-shaped 3 km path: 2 km north, then 1 km east, a fix every 10 m. Offsets shift it sideways. */
    fun lPath(offsetEast: Double = 0.0, skipFirstM: Double = 0.0, reverse: Boolean = false): List<PreviewRow> {
        val pts = mutableListOf<Pair<Double, Double>>()
        var d = 0.0
        while (d <= 3000.0) {
            if (d >= skipFirstM) pts += if (d <= 2000.0) (offsetEast to d) else (offsetEast + d - 2000.0 to 2000.0)
            d += 10.0
        }
        val ordered = if (reverse) pts.reversed() else pts
        return ordered.mapIndexed { i, (e, n) -> row(e, n, i.toDouble()) }
    }

    fun trip(
        id: String,
        startedAt: Long,
        movingS: Double = 300.0,
        kind: TripKind = TripKind.DRIVER,
        badge: QualityBadge = QualityBadge.GOOD,
    ) = Trip(
        id = id, status = TripStatus.FINALISED, kind = kind, source = TripSource.AUTO, startedAt = startedAt,
        startTzOffsetMin = 0, distanceM = 3000.0, movingS = movingS,
        quality = TripQuality(badge, null, Placement.MOUNTED, 50.0, 99.0, null, null, 1.0, 0.0, 1.0, emptyList()),
        createdAt = startedAt, updatedAt = startedAt,
    )

    fun score(tripId: String, smoothness: Int): Score =
        Score(
            tripId, smoothness, smoothness.toDouble(), null, null, null, null, null, emptyList(), null, null, null, null,
            "smooth-1.0.0", 0L,
        )
}

/** An in-memory [RouteHistory]. */
internal class FakeRouteHistory(
    val routes: MutableList<Route> = mutableListOf(),
    val previews: MutableMap<String, List<PreviewRow>> = mutableMapOf(),
    val runs: MutableMap<String, List<ScoredTrip>> = mutableMapOf(),
    val bests: MutableMap<String, PersonalBest> = mutableMapOf(),
) : RouteHistory {
    override suspend fun routes(): List<Route> = routes

    override suspend fun runs(routeId: String, limit: Int): List<ScoredTrip> =
        runs[routeId].orEmpty().sortedByDescending { it.trip.startedAt }.take(limit)

    override suspend fun personalBest(routeId: String): PersonalBest? = bests[routeId]

    override suspend fun preview(tripId: String): List<PreviewRow>? = previews[tripId]
}
