package com.driveperf.app.bridge

import com.driveperf.domain.trip.Placement
import com.driveperf.domain.trip.QualityBadge
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.domain.trip.Trip
import com.driveperf.domain.trip.TripCorrection
import com.driveperf.domain.trip.TripKind
import com.driveperf.domain.trip.TripQuality
import com.driveperf.domain.trip.TripSource
import com.driveperf.domain.trip.TripStatus
import com.driveperf.feature.drives.RecordedStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** DRIVE-109 and DRIVE-100: core trips as the Drives screens list them. */
class TripListBridgeTest {
    private val base = Trip(
        id = "t", status = TripStatus.FINALISED, kind = TripKind.DRIVER, source = TripSource.AUTO, startedAt = 1_000L,
        startTzOffsetMin = 0, endedAt = 601_000L, distanceM = 5_000.0, estimatedDistanceM = 120.0, durationS = 600.0,
        stopCount = 2, createdAt = 0L, updatedAt = 0L,
        quality = TripQuality(QualityBadge.GOOD, null, Placement.MOUNTED, 50.0, 99.0, 40.0, 120.0, 590.0, 10.0, 600.0, emptyList()),
    )

    @Test
    fun `interrupted and short trips keep their status`() {
        assertEquals(RecordedStatus.FINALISED, PlatformTripListSource.recorded(base).status)
        assertEquals(RecordedStatus.INTERRUPTED, PlatformTripListSource.recorded(base.copy(status = TripStatus.INTERRUPTED)).status)
        assertEquals(RecordedStatus.SHORT, PlatformTripListSource.recorded(base.copy(source = TripSource.MANUAL, distanceM = 300.0)).status)
        val shortAuto = PlatformTripListSource.recorded(base.copy(distanceM = 300.0))
        assertEquals("auto trips are never short", RecordedStatus.FINALISED, shortAuto.status)
    }

    @Test
    fun `quality, question and correction cross to the list`() {
        val r = PlatformTripListSource.recorded(base.copy(suspect = Suspect.TRANSIT))
        assertEquals(QualityBadge.GOOD, r.quality?.badge)
        assertEquals(Suspect.TRANSIT, r.question)
        assertEquals(120.0, r.estimatedDistanceM, 0.0)
        assertEquals(601_000L, r.endedWallMs)
        assertNull(PlatformTripListSource.recorded(base.copy(suspect = Suspect.TRANSIT, suspectAnswer = SuspectAnswer.NO)).question)
        val bike = PlatformTripListSource.recorded(
            base.copy(kind = TripKind.NOT_DRIVING, correction = TripCorrection.BIKE, correctedAt = 9L),
        )
        assertEquals(TripCorrection.BIKE, bike.correction)
        assertEquals(9L, bike.correctedAtMs)
    }
}
