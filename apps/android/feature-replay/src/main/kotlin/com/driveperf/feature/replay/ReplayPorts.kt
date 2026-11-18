package com.driveperf.feature.replay

import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.replay.PlaybackSpeed
import kotlinx.coroutines.flow.StateFlow

/**
 * DRIVE-200 SEAM (Pro lock, F-RPL-01 Free/Pro): Replay of drives older than 30 days is Pro (OQ-06), but entitlements
 * do not exist yet (ADR-006). The app binds [NotBuilt] until they do; then it binds the entitlement check, and the
 * screen already shows its Lock state for `true`.
 */
fun interface ReplayProLock {
    suspend fun isLocked(tripId: String): Boolean

    companion object {
        /** No entitlements yet: nothing is locked. */
        val NotBuilt: ReplayProLock = ReplayProLock { false }
    }
}

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

/** Where Replay was opened from (`replay_opened {source}`). */
enum class ReplaySource(val key: String) { TRIP_DETAIL("trip_detail") }

/** Replay analytics (S-RPL-01, F-RPL-01, F-RPL-08). Only these keys; no values beyond them. */
internal sealed class ReplayEvent(
    override val name: String,
    override val properties: Map<String, String> = emptyMap(),
) : AnalyticsEvent {
    class Opened(source: ReplaySource) : ReplayEvent("replay_opened", mapOf("source" to source.key))
    class Played(speed: PlaybackSpeed) : ReplayEvent("replay_played", mapOf("speed" to speed.key))
    data object Scrubbed : ReplayEvent("replay_scrubbed")
    data object Completed : ReplayEvent("replay_completed")
    data object LegendOpened : ReplayEvent("quality_overlay_legend_opened")
}

/** The analytics value of a speed: `auto`, `1x`, `10x`, `30x`. */
internal val PlaybackSpeed.key: String
    get() = when (this) {
        PlaybackSpeed.AUTO -> "auto"
        PlaybackSpeed.X1 -> "1x"
        PlaybackSpeed.X10 -> "10x"
        PlaybackSpeed.X30 -> "30x"
    }
