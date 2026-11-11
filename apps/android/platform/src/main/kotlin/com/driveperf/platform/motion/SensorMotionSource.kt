package com.driveperf.platform.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.driveperf.domain.capture.MotionCapability
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.MotionSink
import com.driveperf.domain.capture.MotionSource
import com.driveperf.domain.capture.RecordedAccelCallback
import com.driveperf.foundation.Clock
import com.driveperf.foundation.DomainError
import com.driveperf.platform.FreshnessFilter
import com.driveperf.platform.capture.CaptureFailureBus

/**
 * [MotionSource] on `SensorManager`: linear acceleration, gravity, gyroscope and the game rotation vector. No
 * magnetometer is used.
 *
 * All sensor callbacks run on one private [HandlerThread], and the [MotionAssembler], the freshness gate and the
 * [MotionBatcher] are only touched from it (spike 1 change 6). The sink is called on that thread and must return
 * quickly; the core enqueues and returns. Nothing is delivered after [stop] returns.
 *
 * Samples that are stale, in the future or out of order are dropped ([FreshnessFilter]): the emulator and some devices
 * deliver mistimed samples right after registering.
 *
 * Without a gyroscope the source reads the raw accelerometer and splits out gravity with a 0.3 Hz low-pass; the
 * capability then reports `hasGyroscope = false` (`NO_GYRO`) and cornering events are off for such trips.
 */
class SensorMotionSource(
    context: Context,
    private val clock: Clock,
    private val failures: CaptureFailureBus,
) : MotionSource {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val capability = MotionCapability(
        hasGyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null,
        maxRateHz = maxRate(),
    )
    private var running: Run? = null

    override fun capability(): MotionCapability = capability

    @Synchronized
    override fun start(rateHz: Int, sink: MotionSink) {
        stop()
        val primary = sensorManager.getDefaultSensor(
            if (capability.hasGyroscope) Sensor.TYPE_LINEAR_ACCELERATION else Sensor.TYPE_ACCELEROMETER,
        )
        if (primary == null) {
            failures.report(DomainError.Capture.SensorUnavailable)
            return
        }
        val thread = HandlerThread("dp-motion").also { it.start() }
        val handler = Handler(thread.looper)
        val batcher = MotionBatcher(sink)
        val gate = FreshnessFilter(maxAgeNs = FreshnessFilter.MOTION_MAX_AGE_NS)
        val deliver: (MotionSample) -> Unit = { sample ->
            if (gate.accept(sample.tMonoNs, clock.monotonicNs())) batcher.add(sample)
        }
        val listener = if (capability.hasGyroscope) FullListener(deliver) else NoGyroListener(deliver)
        val sensors = if (capability.hasGyroscope) {
            listOfNotNull(
                primary,
                sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
            )
        } else {
            listOf(primary)
        }
        val current = Run(thread, handler, listener, sensors)
        running = current
        register(current, rateHz)
    }

    @Synchronized
    override fun setRate(rateHz: Int) {
        val current = running ?: return
        sensorManager.unregisterListener(current.listener)
        register(current, rateHz)
    }

    @Synchronized
    override fun stop() {
        val current = running ?: return
        running = null
        sensorManager.unregisterListener(current.listener)
        // quit() discards queued events, so the sink hears nothing after stop() returns; a batch still filling (under
        // 200 ms of data) is dropped with them.
        current.thread.quit()
    }

    /** Android keeps no accelerometer history for apps, so there is nothing to answer with. */
    override fun queryRecordedAccelerometer(fromWallMs: Long, toWallMs: Long, callback: RecordedAccelCallback) {
        callback.onResult(emptyList())
    }

    private fun register(current: Run, rateHz: Int) {
        val periodUs = MICROS_PER_SECOND / rateHz.coerceAtLeast(1)
        for (sensor in current.sensors) {
            sensorManager.registerListener(current.listener, sensor, periodUs, MAX_REPORT_LATENCY_US, current.handler)
        }
    }

    private fun maxRate(): Int {
        val minDelayUs = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.minDelay ?: 0
        return if (minDelayUs > 0) MICROS_PER_SECOND / minDelayUs else DEFAULT_MAX_RATE_HZ
    }

    private class Run(
        val thread: HandlerThread,
        val handler: Handler,
        val listener: SensorEventListener,
        val sensors: List<Sensor>,
    )

    /** Four-sensor path. Runs only on the sensor thread. */
    private class FullListener(private val deliver: (MotionSample) -> Unit) : SensorEventListener {
        private val assembler = MotionAssembler()

        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            when (event.sensor.type) {
                Sensor.TYPE_GRAVITY -> assembler.onGravity(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                Sensor.TYPE_GYROSCOPE -> assembler.onGyro(v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                Sensor.TYPE_GAME_ROTATION_VECTOR -> assembler.onRotation(
                    v[0].toDouble(), v[1].toDouble(), v[2].toDouble(), if (v.size > 3) v[3].toDouble() else null,
                )
                Sensor.TYPE_LINEAR_ACCELERATION -> assembler
                    .onLinearAcceleration(event.timestamp, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
                    ?.let(deliver)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** `NO_GYRO` path: raw accelerometer, gravity by low-pass, zero rotation rate. Runs only on the sensor thread. */
    private class NoGyroListener(private val deliver: (MotionSample) -> Unit) : SensorEventListener {
        private val estimator = GravityEstimator()
        private val assembler = MotionAssembler()

        override fun onSensorChanged(event: SensorEvent) {
            val v = event.values
            val g = estimator.update(event.timestamp, v[0].toDouble(), v[1].toDouble(), v[2].toDouble())
            assembler.onGravity(g[0], g[1], g[2])
            assembler.onGyro(0.0, 0.0, 0.0)
            assembler
                .onLinearAcceleration(event.timestamp, v[0] - g[0], v[1] - g[1], v[2] - g[2])
                ?.let(deliver)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000
        const val DEFAULT_MAX_RATE_HZ = 50
        const val MAX_REPORT_LATENCY_US = 500_000
    }
}
