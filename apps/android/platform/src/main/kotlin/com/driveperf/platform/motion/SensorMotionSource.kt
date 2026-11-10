package com.driveperf.platform.motion

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import com.driveperf.platform.ports.CaptureFailure
import com.driveperf.platform.ports.MotionCapabilities
import com.driveperf.platform.ports.MotionSample
import com.driveperf.platform.ports.MotionSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * [MotionSource] on `SensorManager`: linear acceleration, gravity, gyroscope and the game rotation vector at 50 Hz.
 * No magnetometer is used.
 *
 * All sensor callbacks run on one private [HandlerThread], and the [MotionAssembler] is only touched from it. Samples
 * cross to the collector through a bounded channel with `trySend`, so a slow collector never blocks the sensor
 * thread; dropped samples are counted in [droppedSamples] (the capture-quality figure, never silent loss).
 *
 * Without a gyroscope the source reads the raw accelerometer and splits out gravity with a 0.3 Hz low-pass,
 * reporting [MotionCapabilities.hasGyro] = false (`NO_GYRO`): cornering events are off for such trips.
 */
class SensorMotionSource(context: Context) : MotionSource {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val dropped = AtomicLong(0)

    /** Samples that could not be delivered because the collector fell behind. */
    val droppedSamples: Long get() = dropped.get()

    override val capabilities: MotionCapabilities =
        MotionCapabilities(hasGyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null)

    override fun samples(): Flow<MotionSample> = callbackFlow {
        val linear = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val primary = if (capabilities.hasGyro) linear else accel
        if (primary == null) {
            close(CaptureFailure.SensorUnavailable("accelerometer"))
            return@callbackFlow
        }

        val thread = HandlerThread("dp-motion").also { it.start() }
        val handler = Handler(thread.looper)
        val deliver: (MotionSample) -> Unit = { sample -> if (trySend(sample).isFailure) dropped.incrementAndGet() }
        val listener = if (capabilities.hasGyro) FullListener(deliver) else NoGyroListener(deliver)
        val sensors = if (capabilities.hasGyro) {
            listOfNotNull(
                linear,
                sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY),
                sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE),
                sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR),
            )
        } else {
            listOf(primary)
        }
        for (sensor in sensors) {
            sensorManager.registerListener(listener, sensor, SAMPLING_PERIOD_US, MAX_REPORT_LATENCY_US, handler)
        }
        awaitClose {
            sensorManager.unregisterListener(listener)
            thread.quitSafely()
        }
    }.buffer(CHANNEL_CAPACITY)

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
        const val SAMPLING_PERIOD_US = 20_000 // 50 Hz
        const val MAX_REPORT_LATENCY_US = 500_000
        const val CHANNEL_CAPACITY = 2_048 // about 40 s at 50 Hz
    }
}
