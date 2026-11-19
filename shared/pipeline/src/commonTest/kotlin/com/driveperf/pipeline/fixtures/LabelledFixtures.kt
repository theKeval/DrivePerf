package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.trip.EventType
import com.driveperf.pipeline.score.LabelledEvent
import com.driveperf.testing.SyntheticCapture
import com.driveperf.testing.SyntheticCapture.Mount

/**
 * The synthetic labelled set DRIVE-93 ships until the A19 field set exists: drives whose hard manoeuvres are labelled
 * by construction, at the middle of each manoeuvre, among gentle ones that are not events.
 */
internal object LabelledFixtures {
    data class Labelled(val fixture: DriveFixtures.Fixture, val labels: List<LabelledEvent>)

    /** About 7 min of city driving: three hard pull-aways, three hard turns and three hard stops, plus gentle driving. */
    val city: Labelled by lazy {
        val c = SyntheticCapture(Mount.rotated(Mount.PORTRAIT_UPRIGHT, 15.0, 10.0), startLat = 4.5, startLng = -29.5, seed = 44)
        val labels = mutableListOf<LabelledEvent>()
        fun hard(type: EventType, manoeuvre: () -> Unit) {
            val t0 = c.seconds
            manoeuvre()
            labels += LabelledEvent(type, (t0 + c.seconds) / 2)
        }
        c.activity(ActivityType.AUTOMOTIVE, 0.0)
        c.cruise(5.0)
        c.speedTo(13.9, 1.4, 1.5)
        c.cruise(40.0)
        c.speedTo(8.0, 1.2, 1.5)
        c.turn(90.0, 9.0)
        c.speedTo(12.0, 1.4, 1.5)
        c.cruise(20.0)
        c.stop(1.6, 1.5, 10.0)
        for (k in 0 until 3) {
            // Short manoeuvres, so the peak is within the 1.0 s match of the labelled middle wherever noise puts it.
            hard(EventType.ACCEL) { c.speedTo(6.0, 3.5, 6.0) }
            c.speedTo(12.0, 1.4, 1.5)
            c.cruise(20.0)
            c.speedTo(8.0, 1.2, 1.5)
            hard(EventType.CORNER) { c.turn(if (k % 2 == 0) 90.0 else -90.0, 5.0) }
            c.cruise(10.0)
            c.turn(if (k % 2 == 0) -90.0 else 90.0, 9.0) // 0.22 g at 8 m/s: not an event
            c.cruise(10.0)
            hard(EventType.BRAKE) { c.speedTo(0.0, 4.0, 6.0) }
            c.cruise(15.0)
            c.speedTo(10.0, 1.4, 1.5)
            c.cruise(10.0)
            c.stop(1.6, 1.5, 15.0)
        }
        Labelled(DriveFixtures.Fixture("syn-labelled-city-01", "Labelled: three hard pull-aways, turns and stops", c.streams()), labels)
    }

    val all: List<Labelled> get() = listOf(city)
}
