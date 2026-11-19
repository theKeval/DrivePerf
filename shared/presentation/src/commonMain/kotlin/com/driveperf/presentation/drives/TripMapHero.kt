package com.driveperf.presentation.drives

import com.driveperf.domain.replay.ReplayDetailsRepository
import com.driveperf.domain.replay.ReplayRepository
import com.driveperf.presentation.analysis.AnalysisMap
import com.driveperf.presentation.analysis.MapPin
import com.driveperf.presentation.analysis.MapPoint
import com.driveperf.presentation.analysis.MapSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * The S-TRP-01 map hero (DRIVE-55): the route drawn flat from the preview track, the same projection as the S-ANL-01
 * map card ([AnalysisMap]), with no map tiles, so it works offline, needs no Maps key and shows no street names
 * (sprint 4 decision 2). Coordinates are metres east ([MapPoint.x]) and north ([MapPoint.y]) of the route's south-west
 * corner; the apps fit [widthM] × [heightM] into the card, north up. Tapping it opens Replay.
 *
 * @property segments the route in its quality styles: estimated start dashed, GPS gaps dotted, excluded dimmed.
 * @property pins the pinned events (a position in measured time); none while the trip's scores are hidden.
 * @property stops the pinned stops of at least 10 s, as small capsules.
 * @property maxSpeed the neutral max-speed triangle; never emphasised.
 * @property estimatedStart shows the "Estimated start" chip over the map.
 * @property accessibilityLabel "Map of your drive, 39.0 km. 2 events, 2 stops."
 */
public data class TripMapUi(
    val widthM: Double,
    val heightM: Double,
    val segments: List<MapSegment>,
    val pins: List<MapPin>,
    val stops: List<MapPoint>,
    val maxSpeed: MapPoint?,
    val estimatedStart: Boolean = false,
    val accessibilityLabel: String = "",
)

/** Loads the map hero of one trip; null when the trip has no route to draw. */
public fun interface TripMapSource {
    public suspend fun map(tripId: String): TripMapUi?
}

/**
 * [TripMapSource] from the trip's replay track and its pins ([ReplayDetailsRepository], which leaves out events
 * reported "Not accurate?"). The route is simplified on [compute], off the main thread.
 */
public class TripMaps(
    private val replays: ReplayRepository,
    private val details: ReplayDetailsRepository,
    private val compute: CoroutineContext = Dispatchers.Default,
) : TripMapSource {
    override suspend fun map(tripId: String): TripMapUi? {
        val replay = replays.replay(tripId)?.takeIf { it.track.size >= MIN_POINTS } ?: return null
        val pins = details.details(tripId, replay)
        return withContext(compute) {
            val map = AnalysisMap(replay, pins?.markers.orEmpty())
            TripMapUi(
                widthM = map.widthM,
                heightM = map.heightM,
                segments = map.segments,
                pins = map.pins,
                stops = pins?.stops.orEmpty().mapNotNull { s ->
                    val lat = s.lat
                    val lng = s.lng
                    if (lat == null || lng == null) null else map.project(lat, lng)
                },
                maxSpeed = pins?.maxSpeed?.let { map.project(it.lat, it.lng) },
            )
        }
    }

    private companion object {
        /** A route needs two points to draw. */
        const val MIN_POINTS = 2
    }
}

/** "Map of your drive, 39.0 km. 2 events, 2 stops." (DRIVE-55 accessibility; no place names in v1). */
internal fun tripMapLabel(distance: String, events: Int, stops: Int): String {
    val eventWord = if (events == 1) "1 event" else "$events events"
    val stopWord = if (stops == 1) "1 stop" else "$stops stops"
    return "Map of your drive, $distance. $eventWord, $stopWord."
}
