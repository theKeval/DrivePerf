package com.driveperf.feature.replay

import kotlinx.coroutines.flow.StateFlow

/**
 * What the map can use (ADR-005). [hasMapsKey] is false in builds without `MAPS_API_KEY` (CI, contributors): Replay
 * then draws on the plain canvas. [mapId] is the optional cloud-styled Night Drive Map ID. [online] drives the offline
 * fallback (F-RPL-01 AC5).
 */
class ReplayMapConfig(
    val hasMapsKey: Boolean,
    val mapId: String?,
    val online: StateFlow<Boolean>,
)

/** How the route is drawn. */
enum class MapRenderer {
    /** Google Maps tiles under the route. */
    GOOGLE,

    /** The plain canvas, because this build has no Maps key. */
    CANVAS,

    /** The plain canvas with "Map unavailable offline": no network, or the tiles never loaded (AC5, EC-NET). */
    CANVAS_OFFLINE,
}
