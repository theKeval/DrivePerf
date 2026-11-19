package com.driveperf.feature.replay

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DoubleState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.driveperf.designsystem.DrivePerfTheme
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.SegmentStyle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PatternItem
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay

/** How long the map may take to draw its first tiles before Replay falls back to the plain canvas (AC5). */
private const val TILE_TIMEOUT_MS = 10_000L

/** How the Google map looks: the Night Drive [mapId] if any, dark or light, the floating chrome's insets, its label. */
internal class GoogleMapLook(val mapId: String?, val dark: Boolean, val insets: PaddingValues, val contentDescription: String)

/** The map reports its first tiles, or that they never came (AC5). */
internal class MapEvents(val onLoaded: () -> Unit, val onTilesFailed: () -> Unit)

/**
 * The Google Maps renderer (ADR-005): Maps Compose with the cloud-styled Night Drive Map ID when there is one, else the
 * SDK's own dark or light scheme. Fits the route on load (AC1). The marker is a native flat marker moved from a
 * snapshot of [time], so playback does not recompose the map.
 */
@Suppress("LongParameterList") // the map, its look and events, plus the pins it hosts
@Composable
internal fun ReplayGoogleMap(
    replay: Replay,
    time: DoubleState,
    look: GoogleMapLook,
    events: MapEvents,
    modifier: Modifier = Modifier,
    focus: LatLng? = null,
    pins: @Composable (MapProjector) -> Unit = {},
) {
    val camera = rememberCameraPositionState()
    val density = LocalDensity.current
    var loaded by remember { mutableStateOf(false) }
    val latestFailed by rememberUpdatedState(events.onTilesFailed)
    LaunchedEffect(Unit) {
        delay(TILE_TIMEOUT_MS)
        if (!loaded) latestFailed()
    }
    val bucket by remember { derivedStateOf { camera.position.zoom.toInt() } }
    // DRIVE-39 AC5: the map centres on the event the evidence sheet is about.
    LaunchedEffect(focus, loaded) {
        if (loaded && focus != null) camera.animate(CameraUpdateFactory.newLatLng(focus))
    }
    Box(modifier) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            contentDescription = look.contentDescription,
            googleMapOptionsFactory = { GoogleMapOptions().apply { look.mapId?.let { mapId(it) } } },
            uiSettings = ReplayUiSettings,
            contentPadding = look.insets,
            mapColorScheme = if (look.dark) ComposeMapColorScheme.DARK else ComposeMapColorScheme.LIGHT,
            onMapLoaded = {
                loaded = true
                val box = replay.bbox
                val bounds = LatLngBounds(LatLng(box.minLat, box.minLng), LatLng(box.maxLat, box.maxLng))
                camera.move(CameraUpdateFactory.newLatLngBounds(bounds, with(density) { FIT_MARGIN.roundToPx() }))
                events.onLoaded()
            },
        ) {
            RouteLines(replay, bucket, density)
            ReplayMarker(replay, time, density)
        }
        if (loaded) {
            // Keyed on the camera position, so the pins follow every pan and zoom.
            val position = camera.position
            val projector = remember(position) {
                val projection = camera.projection
                MapProjector { lat, lng ->
                    projection?.toScreenLocation(LatLng(lat, lng))?.let { Offset(it.x.toFloat(), it.y.toFloat()) }
                }
            }
            pins(projector)
        }
    }
}

private val ReplayUiSettings = MapUiSettings(
    compassEnabled = false,
    indoorLevelPickerEnabled = false,
    mapToolbarEnabled = false,
    myLocationButtonEnabled = false,
    zoomControlsEnabled = false,
    tiltGesturesEnabled = false,
)

/** The route at this zoom bucket (Douglas-Peucker in the shared model), one polyline per style run. */
@Composable
@GoogleMapComposable
private fun RouteLines(replay: Replay, bucket: Int, density: Density) {
    val lines = remember(replay, bucket) {
        replay.segments(bucket)
            .filter { it.points.size >= 2 }
            .sortedBy { DrawOrder.indexOf(it.style) }
            .map { segment -> segment.style to segment.points.map { LatLng(it.lat, it.lng) } }
    }
    val looks = DrawOrder.associateWith { lineLook(it) }
    for ((style, points) in lines) {
        val look = looks.getValue(style)
        look.casing?.let { casing ->
            Polyline(
                points = points,
                color = casing,
                width = with(density) { look.casingWidth.toPx() },
                jointType = JointType.ROUND,
                startCap = RoundCap(),
                endCap = RoundCap(),
            )
        }
        Polyline(
            points = points,
            color = look.color,
            width = with(density) { look.width.toPx() },
            jointType = JointType.ROUND,
            pattern = look.pattern(density),
            startCap = RoundCap(),
            endCap = RoundCap(),
            zIndex = 1f,
        )
    }
}

/** A native marker moved from snapshots of [time]: no recomposition per frame. */
@OptIn(MapsComposeExperimentalApi::class)
@Composable
@GoogleMapComposable
private fun ReplayMarker(replay: Replay, time: DoubleState, density: Density) {
    val data = DrivePerfTheme.dataColors
    val ring = data.route.toArgb()
    val dot = data.marker.toArgb()
    MapEffect(replay) { map ->
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(replay.bbox.minLat, replay.bbox.minLng))
                .icon(BitmapDescriptorFactory.fromBitmap(markerBitmap(density, ring, dot)))
                .anchor(0.5f, 0.5f)
                .flat(true)
                .zIndex(2f),
        ) ?: return@MapEffect
        try {
            snapshotFlow { time.doubleValue }.collect { t -> marker.moveTo(replay, t) }
        } finally {
            marker.remove()
        }
    }
}

private val FIT_MARGIN = 32.dp

private fun Marker.moveTo(replay: Replay, tS: Double) {
    val frame = replay.frameAt(tS)
    // EC-GPS/EC-TUN: the marker hides inside a gap and reappears after it.
    isVisible = frame.style != SegmentStyle.NO_GPS
    position = LatLng(frame.lat, frame.lng)
    frame.bearingDeg?.let { rotation = it.toFloat() }
}

private fun LineLook.pattern(density: Density): List<PatternItem>? = if (dash.size < 2) {
    null
} else {
    with(density) { listOf(Dash(dash[0].toPx()), Gap(dash[1].toPx())) }
}

/** The S-RPL-01 marker as a bitmap, heading north: halo, wedge, ring, dot. Drawn once per map. */
private fun markerBitmap(density: Density, ring: Int, dot: Int): Bitmap = with(density) {
    val size = MarkerLook.halo.toPx().coerceAtLeast((MarkerLook.dot + MarkerLook.wedge).toPx() * 2)
    val bitmap = createBitmap(size.toInt(), size.toInt())
    val canvas = Canvas(bitmap)
    val c = size / 2
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val dotR = MarkerLook.dot.toPx() / 2
    val ringR = dotR + MarkerLook.ring.toPx()
    paint.color = ring
    paint.alpha = (MarkerLook.HALO_ALPHA * FULL_ALPHA).toInt()
    canvas.drawCircle(c, c, MarkerLook.halo.toPx() / 2, paint)
    paint.alpha = FULL_ALPHA
    val half = Math.toRadians(MarkerLook.WEDGE_DEGREES / 2.0)
    val tip = ringR + MarkerLook.wedge.toPx() - dotR
    val wedge = android.graphics.Path().apply {
        moveTo(c, c - tip)
        lineTo(c + (ringR * kotlin.math.sin(half)).toFloat(), c - (ringR * kotlin.math.cos(half)).toFloat())
        lineTo(c - (ringR * kotlin.math.sin(half)).toFloat(), c - (ringR * kotlin.math.cos(half)).toFloat())
        close()
    }
    canvas.drawPath(wedge, paint)
    canvas.drawCircle(c, c, ringR, paint)
    paint.color = dot
    canvas.drawCircle(c, c, dotR, paint)
    bitmap
}

private const val FULL_ALPHA = 255
