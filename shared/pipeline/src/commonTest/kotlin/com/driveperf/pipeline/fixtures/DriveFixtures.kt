package com.driveperf.pipeline.fixtures

import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.WakeKind
import com.driveperf.testing.SyntheticCapture
import com.driveperf.testing.SyntheticCapture.Mount

/**
 * The three synthetic drive fixtures DRIVE-62 ships (city, highway, mixed with a tunnel gap), built in memory so the
 * same tests run on the JVM and the iOS simulator. `FixtureFilesTest` checks the committed CSVs equal these.
 */
internal object DriveFixtures {
    data class Fixture(val id: String, val note: String, val streams: SyntheticCapture.Streams) {
        fun capture(wake: WakeKind = WakeKind.MANUAL, parking: Parking? = null): CapturedTrip {
            val header = CaptureHeader(
                tripId = id, startMonoNs = SyntheticCapture.START_MONO_NS, startWallMs = SyntheticCapture.WALL0,
                tzOffsetMin = -300, tzId = "Etc/GMT+5", wakeKind = wake, sourceDetail = null, nominalImuHz = 50, parking = parking,
            )
            return CapturedTrip(header, streams.fixes, streams.motion, streams.activity, emptyList(), emptyList())
        }
    }

    /** About 5 min of city driving: three signal stops, four turns, one brisk pull-away, dash-mounted and tilted. */
    val city: Fixture by lazy {
        val c = SyntheticCapture(Mount.rotated(Mount.PORTRAIT_UPRIGHT, 25.0, 15.0), startLat = 2.5, startLng = -27.5, seed = 11)
        c.activity(ActivityType.AUTOMOTIVE, 0.0)
        c.cruise(5.0)
        for (block in 0 until 3) {
            c.speedTo(13.9, if (block == 1) 3.2 else 1.6, if (block == 1) 6.0 else 1.5)
            c.cruise(25.0)
            c.speedTo(8.0, 1.5, 1.5)
            c.turn(if (block % 2 == 0) 90.0 else -90.0, if (block == 0) 5.0 else 9.0)
            c.speedTo(12.0, 1.4, 1.5)
            c.cruise(15.0)
            c.stop(if (block == 2) 3.8 else 1.8, if (block == 2) 6.0 else 1.5, 20.0)
        }
        c.speedTo(8.0, 1.5, 1.5)
        c.turn(90.0, 9.0)
        c.cruise(20.0)
        c.stop(1.5, 1.2, 8.0)
        Fixture("syn-city-01", "City loop, three stops, one brisk pull-away and one hard stop", c.streams())
    }

    /** About 6 min on a highway: merge to 30 m/s, gentle lane changes, exit and stop. Phone flat on the console. */
    val highway: Fixture by lazy {
        val c = SyntheticCapture(Mount.rotated(Mount.FLAT, -10.0, 0.0), startLat = 1.5, startLng = -26.5, seed = 22)
        c.activity(ActivityType.AUTOMOTIVE, 0.0)
        c.cruise(3.0)
        c.speedTo(14.0, 1.5, 1.0)
        c.turn(-30.0, 8.0)
        c.speedTo(30.0, 1.4, 0.8)
        for (i in 0 until 10) {
            c.cruise(20.0)
            c.turn(if (i % 2 == 0) 4.0 else -4.0, 4.0)
        }
        c.speedTo(15.0, 1.2, 0.8)
        c.turn(45.0, 10.0)
        c.stop(1.5, 1.0, 10.0)
        Fixture("syn-highway-01", "Highway merge, steady cruise with lane changes, exit", c.streams())
    }

    /** About 6 min: city, then an arterial with a 60 s tunnel (no GNSS, IMU good), then city again. */
    val mixedTunnel: Fixture by lazy {
        val c = SyntheticCapture(Mount.rotated(Mount.PORTRAIT_UPRIGHT, -40.0, 20.0), startLat = 3.5, startLng = -28.5, seed = 33)
        c.activity(ActivityType.AUTOMOTIVE, 0.0)
        c.cruise(4.0)
        c.speedTo(8.0, 1.6, 1.5)
        c.turn(90.0, 9.0)
        c.speedTo(13.0, 1.6, 1.5)
        c.cruise(30.0)
        c.speedTo(24.0, 1.4, 1.0)
        c.cruise(30.0)
        c.tunnel(60.0) {
            cruise(20.0)
            turn(-20.0, 8.0)
            cruise(32.0)
        }
        c.cruise(40.0)
        c.speedTo(8.0, 1.8, 1.5)
        c.turn(-90.0, 9.0)
        c.cruise(20.0)
        c.stop(2.0, 1.5, 15.0)
        c.speedTo(12.0, 1.6, 1.5)
        c.cruise(30.0)
        c.stop(1.6, 1.2, 8.0)
        Fixture("syn-mixed-tunnel-01", "City, arterial with a 60 s tunnel (no GNSS), city", c.streams())
    }

    val all: List<Fixture> get() = listOf(city, highway, mixedTunnel)

    /** A parking spot about 150 m south of the city fixture's start, parked an hour before. */
    val cityParking: Parking = Parking(GeoPoint(2.5 - 150.0 / 111_195.0, -27.5), SyntheticCapture.WALL0 - 3_600_000)
}
