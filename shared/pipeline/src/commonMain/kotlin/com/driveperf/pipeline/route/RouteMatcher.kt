package com.driveperf.pipeline.route

import com.driveperf.domain.trip.PreviewRow
import com.driveperf.pipeline.geo.Geo

/** Route matching thresholds (§7, P-20). */
public data class RouteConfig(
    val radiusM: Double = 200.0,
    val startAllowanceM: Double = 1000.0,
    val minStepM: Double = 50.0,
    val maxPoints: Int = 400,
    val lengthRatioMin: Double = 0.85,
    val lengthRatioMax: Double = 1.15,
    val acceptM: Double = 150.0,
    val similarityScaleM: Double = 300.0,
    val minGnssPct: Double = 60.0,
)

/** One side of a comparison: a trip's preview, or a route's endpoints with its reference trip's preview. */
internal data class Side(val startLat: Double, val startLng: Double, val endLat: Double, val endLng: Double, val rows: List<PreviewRow>) {
    companion object {
        /** Start is the first row (the parking spot when the start was estimated); end is the last gated row. */
        fun ofTrip(rows: List<PreviewRow>): Side? {
            val first = rows.firstOrNull()
            val last = rows.lastOrNull { it.quality == 0 }
            return if (first == null || last == null) null else Side(first.lat, first.lng, last.lat, last.lng, rows)
        }
    }
}

/** §7.2 endpoint test and §7.3 path similarity. Direction is intrinsic: A→B never matches B→A. */
internal class RouteMatcher(private val cfg: RouteConfig = RouteConfig()) {
    fun endpointsMatch(trip: Side, route: Side): Boolean {
        val r = cfg.radiusM
        val startOk = Geo.haversineM(trip.startLat, trip.startLng, route.startLat, route.startLng) <= r ||
            near(route.startLat, route.startLng, RouteGeometry.firstMetres(trip.rows, cfg.startAllowanceM)) ||
            near(trip.startLat, trip.startLng, RouteGeometry.firstMetres(route.rows, cfg.startAllowanceM))
        val endOk = Geo.haversineM(trip.endLat, trip.endLng, route.endLat, route.endLng) <= r
        return startOk && endOk
    }

    /**
     * The Fréchet distance in metres when the paths match (≤ 150 m), else null. When one start lies on the other path's
     * first kilometre (the §7.2 start-latency clause), that path's lead-in is trimmed to the point nearest the other
     * start first, so start latency does not dominate the distance (G-20).
     */
    @Suppress("ReturnCount")
    fun frechet(trip: Side, route: Side): Double? {
        val (tripRows, routeRows) = trimLeadIn(RouteGeometry.usable(trip.rows), RouteGeometry.usable(route.rows))
        val a = RouteGeometry.project(tripRows, route.startLat, route.startLng)
        val b = RouteGeometry.project(routeRows, route.startLat, route.startLng)
        val la = RouteGeometry.length(a)
        val lb = RouteGeometry.length(b)
        if (la <= 0 || lb <= 0) return null
        val ratio = la / lb
        if (ratio < cfg.lengthRatioMin || ratio > cfg.lengthRatioMax) return null
        val f = RouteGeometry.frechet(
            RouteGeometry.resample(a, maxOf(cfg.minStepM, la / cfg.maxPoints)),
            RouteGeometry.resample(b, maxOf(cfg.minStepM, lb / cfg.maxPoints)),
        )
        return f.takeIf { it <= cfg.acceptM }
    }

    fun similarity(frechetM: Double): Double = 1 - minOf(1.0, frechetM / cfg.similarityScaleM)

    private fun trimLeadIn(trip: List<PreviewRow>, route: List<PreviewRow>): Pair<List<PreviewRow>, List<PreviewRow>> {
        val ts = trip.firstOrNull()
        val rs = route.firstOrNull()
        if (ts == null || rs == null || Geo.haversineM(ts.lat, ts.lng, rs.lat, rs.lng) <= cfg.radiusM) return trip to route
        return when {
            near(ts.lat, ts.lng, RouteGeometry.firstMetres(route, cfg.startAllowanceM)) -> trip to route.drop(nearestIndex(route, ts))
            near(rs.lat, rs.lng, RouteGeometry.firstMetres(trip, cfg.startAllowanceM)) -> trip.drop(nearestIndex(trip, rs)) to route
            else -> trip to route
        }
    }

    private fun nearestIndex(rows: List<PreviewRow>, p: PreviewRow): Int {
        val lead = RouteGeometry.firstMetres(rows, cfg.startAllowanceM)
        return lead.indices.minBy { Geo.haversineM(lead[it].lat, lead[it].lng, p.lat, p.lng) }
    }

    private fun near(lat: Double, lng: Double, rows: List<PreviewRow>): Boolean =
        rows.any { Geo.haversineM(lat, lng, it.lat, it.lng) <= cfg.radiusM }
}
