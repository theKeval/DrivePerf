package com.driveperf.platform.capture

import com.driveperf.platform.ports.ActivityEvent
import com.driveperf.platform.ports.ActivitySource
import com.driveperf.platform.ports.CaptureFailure
import com.driveperf.platform.ports.Clock
import com.driveperf.platform.ports.LocationSample
import com.driveperf.platform.ports.LocationSource
import com.driveperf.platform.ports.MotionCapabilities
import com.driveperf.platform.ports.MotionSample
import com.driveperf.platform.ports.MotionSource
import com.driveperf.platform.ports.TripFileStore
import com.driveperf.platform.ports.TripFileWriter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow

class FakeClock(var monoNs: Long = 0, var wallMs: Long = 1_000_000) : Clock {
    override fun monotonicNs() = monoNs
    override fun wallMs() = wallMs
}

class FakeLocationSource : LocationSource {
    val flow = MutableSharedFlow<LocationSample>(extraBufferCapacity = 64)
    var failWith: CaptureFailure? = null
    override fun samples(): Flow<LocationSample> = failWith?.let { failure -> flow<LocationSample> { throw failure } } ?: flow
}

class FakeMotionSource : MotionSource {
    val flow = MutableSharedFlow<MotionSample>(extraBufferCapacity = 64)
    var failWith: CaptureFailure? = null
    override val capabilities = MotionCapabilities(hasGyro = true)
    override fun samples(): Flow<MotionSample> = failWith?.let { failure -> flow<MotionSample> { throw failure } } ?: flow
}

class FakeActivitySource : ActivitySource {
    val flow = MutableSharedFlow<ActivityEvent>(extraBufferCapacity = 64)
    var failWith: CaptureFailure? = null
    override fun events(): Flow<ActivityEvent> = failWith?.let { failure -> flow<ActivityEvent> { throw failure } } ?: flow
}

class FakeStore : TripFileStore {
    val writers = mutableMapOf<String, FakeWriter>()
    val deleted = mutableListOf<String>()
    override fun open(tripId: String): TripFileWriter = FakeWriter().also { writers[tripId] = it }
    override fun delete(tripId: String) {
        deleted += tripId
    }
}

class FakeWriter : TripFileWriter {
    val locations = mutableListOf<LocationSample>()
    val motions = mutableListOf<MotionSample>()
    val activities = mutableListOf<ActivityEvent>()
    var flushes = 0
    var closed = false

    @Synchronized override fun append(sample: LocationSample) { locations += sample }
    @Synchronized override fun append(sample: MotionSample) { motions += sample }
    @Synchronized override fun append(event: ActivityEvent) { activities += event }
    @Synchronized override fun flush() { flushes++ }
    @Synchronized override fun close() { closed = true }
}
