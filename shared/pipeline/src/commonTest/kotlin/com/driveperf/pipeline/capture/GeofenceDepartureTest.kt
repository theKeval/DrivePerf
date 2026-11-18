package com.driveperf.pipeline.capture

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.WakeKind
import com.driveperf.pipeline.capture.CaptureFixtures.activity
import com.driveperf.pipeline.capture.CaptureFixtures.fix
import com.driveperf.pipeline.capture.CaptureFixtures.header
import com.driveperf.pipeline.capture.CaptureFixtures.wallAt
import com.driveperf.pipeline.signal.EstimatedStart
import com.driveperf.pipeline.signal.GnssGate
import com.driveperf.pipeline.signal.SignalConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GeofenceDepartureTest {
    private val capture = CapturedTrip(
        header(0.0).copy(sourceDetail = "geofence"), listOf(fix(5.0, 10.0, 300.0)), emptyList(),
        listOf(activity(2.0, ActivityType.AUTOMOTIVE)), emptyList(), emptyList(),
    )
    private val cfg = SignalConfig()

    private fun estimate(c: CapturedTrip = capture) = EstimatedStart(cfg).estimate(
        c, GnssGate(cfg).gate(c.fixes, c.header.startMonoNs), 900.0,
    )

    @Test
    fun geofenceBeforeVehicleTransitionGetsAnAnchorBasedEstimate() {
        val result = assertNotNull(estimate())
        assertEquals(-25.0, result.startOffsetS, 0.1)
        assertEquals(300.0, result.distanceM, 0.1)
    }

    @Test
    fun actualDepartureHistoryTakesPriorityOverProjection() {
        val c = capture.copy(activity = listOf(activity(-60.0, ActivityType.AUTOMOTIVE)))
        assertEquals(-60.0, assertNotNull(estimate(c)).startOffsetS)
    }

    @Test
    fun onlyAndroidGeofenceDeparturesWithEarlyAutomotiveEvidenceProject() {
        for (kind in listOf(WakeKind.MANUAL, WakeKind.MOVE_SIGNAL, WakeKind.ACTIVITY_VEHICLE)) {
            assertNull(estimate(capture.copy(header = capture.header.copy(wakeKind = kind))))
        }
        assertNull(estimate(capture.copy(header = capture.header.copy(sourceDetail = "region_exit"))))
        assertNull(estimate(capture.copy(header = capture.header.copy(parking = null))))
        for (a in listOf(
            emptyList(), listOf(activity(2.0, ActivityType.AUTOMOTIVE, ActivityConfidence.LOW)),
            listOf(activity(181.0, ActivityType.AUTOMOTIVE)),
            listOf(activity(1.0, ActivityType.WALKING), activity(2.0, ActivityType.AUTOMOTIVE)),
        )) assertNull(estimate(capture.copy(activity = a)))
    }

    @Test
    fun projectionKeepsTheExistingDistanceTimeAndParkingBounds() {
        assertNull(estimate(capture.copy(fixes = listOf(fix(5.0, 10.0, 5_100.0)))))
        assertNull(estimate(capture.copy(fixes = listOf(fix(5.0, 3.0, 3_000.0)))))
        assertNull(estimate(capture.copy(fixes = listOf(fix(5.0, 1.0, 300.0)))))
        assertNull(estimate(capture.copy(fixes = listOf(fix(5.0, null, 300.0)))))
        val recent = capture.header.parking!!.copy(wallMs = wallAt(-10.0))
        assertNull(estimate(capture.copy(header = capture.header.copy(parking = recent))))
        assertNull(GeofenceDeparture.startWallMs(capture, 300.0, 5.0, Double.NaN))
        assertNull(GeofenceDeparture.startWallMs(capture, 0.0, 5.0, 10.0))
    }
}
