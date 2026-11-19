package com.driveperf.presentation.analysis

import com.driveperf.domain.analysis.AnalysisDataBuilder
import com.driveperf.domain.analysis.TripAnalysis
import com.driveperf.domain.analytics.Analytics
import com.driveperf.domain.analytics.AnalyticsEvent
import com.driveperf.domain.replay.EventMarkers
import com.driveperf.domain.replay.Replay
import com.driveperf.domain.replay.ReplayPoint
import com.driveperf.domain.replay.ReplaySpan
import com.driveperf.domain.replay.SegmentStyle
import com.driveperf.domain.score.Score
import com.driveperf.domain.settings.Settings
import com.driveperf.domain.settings.SettingsRepository
import com.driveperf.domain.settings.SettingsVehicle
import com.driveperf.domain.trip.BoundingBox
import com.driveperf.domain.trip.Confidence
import com.driveperf.domain.trip.DrivingEvent
import com.driveperf.domain.trip.EventFeedback
import com.driveperf.domain.trip.EventType
import com.driveperf.domain.trip.PreviewRow
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.SegmentReason
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.domain.tripfile.AnalysisFix
import com.driveperf.domain.tripfile.AnalysisTrace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.sin

/**
 * A synthetic drive (no real place): north along a meridian at 0°, 10 m/s with a 0.3 g sine on longitudinal G and a
 * 0.2 g one on lateral G. An estimated start for the first 10 s, a GPS gap at 100–130 s and an excluded stretch at
 * 200–260 s (phone moved). One braking event at 150 s and one cornering event at 300 s, marked needed.
 */
internal object AnalysisTestData {
    const val RATE = 50

    fun events(): List<DrivingEvent> = listOf(
        DrivingEvent("brake", "t1", EventType.BRAKE, 150.0, 149.0, 151.0, 2.0, -0.31, null, lat(150.0), 0.0, 12.0, 6.0, Confidence.HIGH, "v1"),
        DrivingEvent("corner", "t1", EventType.CORNER, 300.0, 299.0, 301.0, 2.0, 0.22, null, lat(300.0), 0.0, 10.0, 10.0, Confidence.HIGH, "v1"),
    )

    fun lat(tS: Double): Double = tS * 0.00009

    val spans = listOf(
        ReplaySpan(SegmentStyle.ESTIMATED, 0.0, 10.0),
        ReplaySpan(SegmentStyle.NO_GPS, 100.0, 130.0),
        ReplaySpan(SegmentStyle.EXCLUDED, 200.0, 260.0, SegmentReason.PHONE_MOVED),
    )

    fun analysis(seconds: Int = 400, withScore: Boolean = true): TripAnalysis {
        val n = seconds * RATE
        val fixTimes = (0..seconds).map { it.toDouble() }.filterNot { it > 100.0 && it < 130.0 }
        val trace = AnalysisTrace(
            rateHz = RATE,
            lonG = DoubleArray(n) { 0.3 * sin(2 * PI * it / (RATE * 20.0)) },
            latG = DoubleArray(n) { 0.2 * sin(2 * PI * it / (RATE * 30.0)) },
            vertG = DoubleArray(n),
            yawDps = DoubleArray(n),
            quality = IntArray(n) { i -> if (i < 10 * RATE) 2 else if (i in 200 * RATE until 260 * RATE) 4 else 0 },
            fixes = fixTimes.map { AnalysisFix(it, 10.0, lat(it), 0.0, 0.5) },
        )
        val preview = (0..seconds step 10).map { PreviewRow(it.toDouble(), lat(it.toDouble()), 0.0, 10.0, 0.0, 0.0, 100.0 + it / 10, 0) }
        val replay = Replay("t1", BoundingBox(0.0, 0.0, lat(seconds.toDouble()), 0.0), seconds.toDouble(), spans, false,
            fixTimes.map { ReplayPoint(it, lat(it), 0.0, 10.0) })
        val trip = Trip(
            id = "t1", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = 0,
            startTzOffsetMin = 0, durationS = seconds.toDouble(), distanceM = seconds * 10.0, maxSpeedMps = 10.0,
            quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 95.0, 5.0, null, seconds - 70.0, 70.0,
                seconds.toDouble(), emptyList()),
            createdAt = 0, updatedAt = 0,
        )
        val events = events()
        val feedback = mapOf("corner" to EventFeedback("corner", "t1", needed = true, inaccurate = null, at = 1))
        val data = AnalysisDataBuilder.build(seconds.toDouble(), trip.distanceM, trace, preview, spans)
        val score = Score("t1", 82, 82.0, null, null, null, null, null, emptyList(), null, null, null, null, "v1", 0)
        return TripAnalysis(trip, replay, data, EventMarkers.build(events, feedback, emptyList()), events, score.takeIf { withScore })
    }
}

internal class RecordedAnalytics : Analytics {
    val tracked = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        tracked += event
    }

    override fun setUsageConsent(allowed: Boolean): Unit = Unit

    override fun resetId(): Unit = Unit

    fun names(): List<String> = tracked.map { it.name }
}

internal class FakeSettings(initial: Settings = Settings()) : SettingsRepository {
    private val mutable = MutableStateFlow(initial)
    override val state: StateFlow<Settings> = mutable
    val saved = mutableListOf<Settings>()

    override suspend fun update(settings: Settings): Boolean {
        saved += settings
        mutable.value = settings
        return true
    }

    override suspend fun vehicles(): List<SettingsVehicle> = emptyList()
}
