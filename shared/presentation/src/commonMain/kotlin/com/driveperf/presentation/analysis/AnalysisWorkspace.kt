package com.driveperf.presentation.analysis

import com.driveperf.domain.analysis.GgCloud
import com.driveperf.domain.analysis.GgPoint
import com.driveperf.domain.analysis.TripAnalysis
import com.driveperf.domain.replay.EventMarker
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.settings.UnitSystem
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.SegmentReason
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot

/**
 * The S-ANL-01 map card: the route projected flat (equirectangular around the trip's middle latitude, plenty for one
 * drive) in metres from the south-west corner, so the apps fit it to their card and convert a tap back with
 * [nearestTime]. Drawn without map tiles: it works offline and needs no Maps key.
 */
public class AnalysisMap internal constructor(private val replay: Replay, markers: List<EventMarker>) {
    private val cosLat: Double = cos((replay.bbox.minLat + replay.bbox.maxLat) / 2 * PI / 180)

    /** The route's extent in metres. */
    public val widthM: Double = project(replay.bbox.maxLat, replay.bbox.maxLng).x

    public val heightM: Double = project(replay.bbox.maxLat, replay.bbox.maxLng).y

    /** The route in its quality styles (estimated dashed, no-GPS dotted, excluded dimmed). */
    public val segments: List<MapSegment> by lazy {
        replay.segments(ZOOM).map { s ->
            val pts = s.points.map { project(it.lat, it.lng) }
            MapSegment(s.style, DoubleArray(pts.size) { pts[it].x }, DoubleArray(pts.size) { pts[it].y })
        }
    }

    /** Pinned events only: a position in measured time (F-RPL-03 AC5). */
    public val pins: List<MapPin> = markers.filter { it.pinned && it.lat != null && it.lng != null }
        .map { m -> MapPin(m.eventId, m.type, m.needed, project(m.lat ?: 0.0, m.lng ?: 0.0)) }

    /** The marker at [tS]; null in a GPS gap, where it is hidden rather than interpolated (F-RPL-08). */
    public fun markerAt(tS: Double): MapPoint? {
        val f = replay.frameAt(tS)
        return if (f.style == SegmentStyle.NO_GPS && f.speedMps == null) null else project(f.lat, f.lng)
    }

    /** The trip time of the track point nearest to [x], [y] (metres): tapping the map snaps the cursor there (AC2). */
    public fun nearestTime(x: Double, y: Double): Double =
        replay.track.minByOrNull { p -> project(p.lat, p.lng).let { hypot(it.x - x, it.y - y) } }?.tS ?: 0.0

    internal fun project(lat: Double, lng: Double): MapPoint =
        MapPoint((lng - replay.bbox.minLng) * M_PER_DEG * cosLat, (lat - replay.bbox.minLat) * M_PER_DEG)

    internal companion object {
        /** Web-mercator zoom for the route's simplification: a city-scale card. */
        const val ZOOM = 15
        const val M_PER_DEG = 6_371_008.8 * PI / 180

        fun of(analysis: TripAnalysis): AnalysisMap? = analysis.replay?.let { AnalysisMap(it, analysis.markers) }
    }
}

/** The stat tiles beside the G-G card and the G-G view, from one [TripAnalysis]. */
internal object AnalysisPanels {
    private const val PART_MIN_S = 30.0
    private const val RING_STEP = 0.1
    private const val RINGS = 5

    /** Smoothness first (speed is neutral and never first), then max speed, then events. */
    fun stats(analysis: TripAnalysis, units: UnitSystem): List<StatTile> {
        val trip = analysis.trip
        val q = trip.quality
        val based = q?.takeIf { it.totalS > 0 }?.let {
            "Based on ${AnalysisCopy.minutes(it.scoredS)} of ${AnalysisCopy.minutes(it.totalS)} min"
        }
        val smooth = analysis.score?.smoothness?.toString() ?: AnalysisCopy.NO_VALUE
        val maxSpeed = (trip.maxSpeedMps ?: analysis.data.speed?.series?.maxValue()?.takeUnless { it.isNaN() })
            ?.let { AnalysisCopy.speed(it, units) } ?: AnalysisCopy.NO_VALUE
        val pinned = analysis.markers.count { it.pinned }
        val events = analysis.markers.size
        return listOf(
            StatTile("Smoothness", smooth, based),
            StatTile("Max speed", maxSpeed, null),
            StatTile("Events", events.toString(), if (events > 0) "$pinned on the map" else null),
        )
    }

    fun gg(analysis: TripAnalysis, filter: EventType?): GgView {
        val cloud = analysis.data.gg
        val dots = cloud.cells.map { GgDot(it.latG, it.lonG, it.weight, filter == null || it.type == filter) }
        val pins = analysis.markers.filter { filter == null || it.type == filter }.mapNotNull { m ->
            val la = analysis.data.latG?.series?.valueAt(m.tPeakS) ?: Double.NaN
            val lo = analysis.data.lonG?.series?.valueAt(m.tPeakS) ?: Double.NaN
            if (la.isNaN() || lo.isNaN()) null else GgPin(m.eventId, m.type, m.needed, GgPoint(la, lo))
        }
        val caption = cloud.withinPct?.let { "$it% of your inputs stayed within 0.2 g" }
        val peaks = peaks(analysis)
        return GgView(
            dots = dots,
            envelope = cloud.envelope,
            pins = pins,
            rings = (1..RINGS).map { GgRing(it * RING_STEP, "${AnalysisCopy.fixed(it * RING_STEP, 1)} g") },
            scaleG = GgCloud.SCALE_G,
            caption = caption,
            filterCaption = filter?.let { "Showing ${AnalysisCopy.typeTitle(it).lowercase()}, ${cloud.momentsByType[it] ?: 0} moments" },
            filters = (listOf<EventType?>(null) + EventType.entries).map { t ->
                GgFilterChip(t, t?.let(AnalysisCopy::typeTitle) ?: "All", t == filter)
            },
            peaks = peaks,
            partBanner = partBanner(analysis),
            accessibilityLabel = listOfNotNull(
                "Friction circle.",
                caption?.let { "$it." } ?: AnalysisCopy.GG_EMPTY,
                peaks.takeIf { it.isNotEmpty() }?.joinToString(", ", "Peaks: ", ".") { "${it.title.lowercase()} ${it.value}" },
            ).joinToString(" "),
        )
    }

    private fun peaks(analysis: TripAnalysis): List<GgPeakRow> = EventType.entries.mapNotNull { type ->
        val ofType = analysis.events.filter { it.type == type }
        val top = ofType.maxByOrNull { abs(it.peakG) } ?: return@mapNotNull null
        val count = if (ofType.size == 1) "1 event" else "${ofType.size} events"
        GgPeakRow(type, AnalysisCopy.typeTitle(type), "$count · largest ${AnalysisCopy.clock(top.tPeakS)} in", AnalysisCopy.g(top.peakG), top.id)
    }

    /** "3 min left out: phone moved. …" (S-ANL-02 Part), when at least half a minute was excluded. */
    private fun partBanner(analysis: TripAnalysis): String? {
        val excluded = analysis.data.bands.filter { it.style == SegmentStyle.EXCLUDED }
        val seconds = excluded.sumOf { it.t1S - it.t0S }
        if (seconds < PART_MIN_S) return null
        val phone = excluded.all { it.reason == null || it.reason == SegmentReason.PHONE_MOVED }
        val why = if (phone) ": phone moved" else ""
        return "${AnalysisCopy.minutes(seconds)} min left out$why. Those moments aren’t plotted, so the circle only shows " +
            "what we could measure fairly."
    }
}
