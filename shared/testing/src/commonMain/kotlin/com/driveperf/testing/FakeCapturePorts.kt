package com.driveperf.testing

import com.driveperf.domain.capture.ActivityHistoryCallback
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivitySink
import com.driveperf.domain.capture.ActivitySource
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.LocationSink
import com.driveperf.domain.capture.LocationSource
import com.driveperf.domain.capture.MotionCapability
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.MotionSink
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.capture.ParkedRegionMonitor
import com.driveperf.domain.capture.PowerSink
import com.driveperf.domain.capture.PowerState
import com.driveperf.domain.capture.PowerStatus
import com.driveperf.domain.capture.RecordedAccelCallback
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.ThermalState

/** A [LocationSource] that tests drive by hand with [emit]. */
public class FakeLocationSource : LocationSource {
    private var sink: LocationSink? = null

    /** True while started. */
    public val isRunning: Boolean get() = sink != null

    override fun start(sink: LocationSink) {
        this.sink = sink
    }

    override fun stop() {
        sink = null
    }

    /** Delivers fixes as the platform would. Ignored while stopped. */
    public fun emit(vararg samples: LocationSample) {
        sink?.onLocations(samples.toList())
    }
}

/** A [MotionSource] that records rate changes and lets tests [emit] samples. */
public class FakeMotionSource(
    private val capability: MotionCapability = MotionCapability(hasGyroscope = true, maxRateHz = 100),
    /** What [queryRecordedAccelerometer] answers. */
    public var recorded: List<RecordedAccelSample> = emptyList(),
) : MotionSource {
    private var sink: MotionSink? = null

    /** The current rate, or null while stopped. */
    public var rateHz: Int? = null
        private set

    /** Every rate set, in order, including the start rate. */
    public val rateHistory: MutableList<Int> = mutableListOf()

    override fun capability(): MotionCapability = capability

    override fun start(rateHz: Int, sink: MotionSink) {
        this.sink = sink
        this.rateHz = rateHz
        rateHistory += rateHz
    }

    override fun setRate(rateHz: Int) {
        this.rateHz = rateHz
        rateHistory += rateHz
    }

    override fun stop() {
        sink = null
        rateHz = null
    }

    /** When true, [queryRecordedAccelerometer] holds its answer until [releaseRecorded]. */
    public var holdRecorded: Boolean = false

    private var heldCallback: RecordedAccelCallback? = null

    override fun queryRecordedAccelerometer(fromWallMs: Long, toWallMs: Long, callback: RecordedAccelCallback) {
        if (holdRecorded) heldCallback = callback else callback.onResult(recorded)
    }

    /** Answers a held query with [recorded]. */
    public fun releaseRecorded() {
        heldCallback?.onResult(recorded)
        heldCallback = null
    }

    /** Delivers samples. Ignored while stopped. */
    public fun emit(vararg samples: MotionSample) {
        sink?.onMotion(samples.toList())
    }
}

/** An [ActivitySource] with a scripted [history]. Records every query window. */
public class FakeActivitySource(public var history: List<ActivitySample> = emptyList()) : ActivitySource {
    private var sink: ActivitySink? = null

    /** `(fromWallMs, toWallMs)` of every history query. */
    public val queries: MutableList<Pair<Long, Long>> = mutableListOf()

    /** True while started. */
    public val isRunning: Boolean get() = sink != null

    override fun start(sink: ActivitySink) {
        this.sink = sink
    }

    override fun stop() {
        sink = null
    }

    override fun queryHistory(fromWallMs: Long, toWallMs: Long, callback: ActivityHistoryCallback) {
        queries += fromWallMs to toWallMs
        callback.onResult(history.filter { it.wallMs in fromWallMs..toWallMs })
    }

    /** Delivers a live change. Ignored while stopped. */
    public fun emit(vararg samples: ActivitySample) {
        sink?.onActivity(samples.toList())
    }
}

/** A [PowerState] whose status tests set with [emit]. */
public class FakePowerState(
    private var status: PowerStatus = PowerStatus(80, isCharging = false, lowPowerMode = false, ThermalState.NOMINAL),
) : PowerState {
    private var sink: PowerSink? = null

    override fun current(): PowerStatus = status

    override fun start(sink: PowerSink) {
        this.sink = sink
    }

    override fun stop() {
        sink = null
    }

    /** Changes the status and notifies a running sink. */
    public fun emit(newStatus: PowerStatus) {
        status = newStatus
        sink?.onPower(newStatus)
    }
}

/** A [ParkedRegionMonitor] that remembers the armed region. */
public class FakeParkedRegionMonitor : ParkedRegionMonitor {
    /** The armed centre and radius, or null. */
    public var armed: Pair<GeoPoint, Double>? = null
        private set

    override fun arm(center: GeoPoint, radiusM: Double) {
        armed = center to radiusM
    }

    override fun disarm() {
        armed = null
    }
}
