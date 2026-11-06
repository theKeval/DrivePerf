package com.driveperf.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.driveperf.MainActivity
import com.driveperf.R
import com.driveperf.data.repository.TripRepository
import com.driveperf.domain.model.DrivingEvent
import com.driveperf.domain.model.EventType
import com.driveperf.domain.scoring.TripQualityScoreCalculator
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/**
 * Foreground service that records an active trip.
 *
 * Lifecycle:
 *  - Started with [ACTION_START_TRIP] (carries tripId as an extra).
 *  - Stopped with [ACTION_STOP_TRIP].
 *
 * While running, it:
 *  1. Requests high-accuracy fused location updates.
 *  2. Listens to the accelerometer and gyroscope.
 *  3. Persists location points and motion windows via [TripRepository].
 *  4. Detects driving events (hard braking, harsh acceleration, etc.) using
 *     simple rule-based thresholds.
 *  5. On stop, computes and saves a [TripScore].
 */
@AndroidEntryPoint
class TripTrackingService : Service(), SensorEventListener {

    companion object {
        const val ACTION_START_TRIP = "com.driveperf.ACTION_START_TRIP"
        const val ACTION_STOP_TRIP = "com.driveperf.ACTION_STOP_TRIP"
        const val EXTRA_TRIP_ID = "extra_trip_id"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "trip_tracking_channel"
        private const val LOCATION_INTERVAL_MS = 2_000L
        private const val LOCATION_MAX_WAIT_MS = 4_000L

        // Event detection thresholds
        private const val HARD_BRAKING_THRESHOLD_MS2 = -5.5f    // m/s² longitudinal
        private const val HARSH_ACCEL_THRESHOLD_MS2 = 4.5f      // m/s²
        private const val CORNERING_THRESHOLD_MS2 = 4.0f         // lateral m/s²
        private const val SPEEDING_THRESHOLD_KMH = 120f
        private const val MOTION_WINDOW_DURATION_MS = 1_000L
    }

    @Inject lateinit var tripRepository: TripRepository
    @Inject lateinit var scoreCalculator: TripQualityScoreCalculator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager

    private var currentTripId: Long = -1L
    private var tripStartTimeMs: Long = 0L
    private var lastLocation: Location? = null
    private var totalDistanceMeters: Double = 0.0
    private val pendingEvents = mutableListOf<DrivingEvent>()

    // Motion window accumulation
    private val accelBuffer = mutableListOf<FloatArray>()
    private val gyroBuffer = mutableListOf<FloatArray>()
    private var windowStartMs: Long = SystemClock.elapsedRealtime()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { processLocation(it) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_TRIP -> {
                val tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L)
                if (tripId != -1L) {
                    currentTripId = tripId
                    tripStartTimeMs = System.currentTimeMillis()
                    startForeground(NOTIFICATION_ID, buildNotification())
                    startLocationUpdates()
                    startSensorListening()
                    serviceScope.launch {
                        tripRepository.activateTrip(tripId)
                    }
                }
            }
            ACTION_STOP_TRIP -> stopTrip()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    // ── Location ──────────────────────────────────────────────────────────

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(LOCATION_INTERVAL_MS)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateDelayMillis(LOCATION_MAX_WAIT_MS)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            stopSelf()
        }
    }

    private fun processLocation(location: Location) {
        // Accumulate distance
        lastLocation?.let { prev ->
            totalDistanceMeters += prev.distanceTo(location).toDouble()
        }
        lastLocation = location

        // Speeding detection (simple threshold, no posted-speed data in MVP)
        val speedKmh = location.speed * 3.6f
        if (speedKmh > SPEEDING_THRESHOLD_KMH) {
            pendingEvents += DrivingEvent(
                tripId = currentTripId,
                type = EventType.SPEEDING,
                timestampMs = System.currentTimeMillis(),
                severity = ((speedKmh - SPEEDING_THRESHOLD_KMH) / 30f).coerceIn(0f, 1f),
                details = "Speed: ${speedKmh.toInt()} km/h",
                latitude = location.latitude,
                longitude = location.longitude,
            )
        }
    }

    // ── Sensors ───────────────────────────────────────────────────────────

    private fun startSensorListening() {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        accel?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyro?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val now = SystemClock.elapsedRealtime()
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelBuffer += event.values.copyOf()
                checkForMotionEvents(event.values)
            }
            Sensor.TYPE_GYROSCOPE -> {
                gyroBuffer += event.values.copyOf()
            }
        }

        // Flush window when interval elapsed
        if (now - windowStartMs >= MOTION_WINDOW_DURATION_MS) {
            flushMotionWindow(now)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    private fun checkForMotionEvents(accelValues: FloatArray) {
        val x = accelValues[0]
        val y = accelValues[1]

        // TODO: Replace with a proper gravity-removal high-pass filter and device-orientation
        //       calibration so that longitudinal/lateral axes map to the vehicle frame correctly.
        val longitudinal = y
        val lateral = x

        val now = System.currentTimeMillis()
        val loc = lastLocation

        when {
            longitudinal < HARD_BRAKING_THRESHOLD_MS2 -> {
                pendingEvents += DrivingEvent(
                    tripId = currentTripId,
                    type = EventType.HARD_BRAKING,
                    timestampMs = now,
                    severity = (abs(longitudinal) / 10f).coerceIn(0f, 1f),
                    details = "Deceleration: ${String.format("%.1f", abs(longitudinal))} m/s²",
                    latitude = loc?.latitude ?: 0.0,
                    longitude = loc?.longitude ?: 0.0,
                )
            }
            longitudinal > HARSH_ACCEL_THRESHOLD_MS2 -> {
                pendingEvents += DrivingEvent(
                    tripId = currentTripId,
                    type = EventType.HARSH_ACCELERATION,
                    timestampMs = now,
                    severity = (longitudinal / 10f).coerceIn(0f, 1f),
                    details = "Acceleration: ${String.format("%.1f", longitudinal)} m/s²",
                    latitude = loc?.latitude ?: 0.0,
                    longitude = loc?.longitude ?: 0.0,
                )
            }
        }

        if (abs(lateral) > CORNERING_THRESHOLD_MS2) {
            pendingEvents += DrivingEvent(
                tripId = currentTripId,
                type = EventType.AGGRESSIVE_CORNERING,
                timestampMs = now,
                severity = (abs(lateral) / 8f).coerceIn(0f, 1f),
                details = "Lateral force: ${String.format("%.1f", abs(lateral))} m/s²",
                latitude = loc?.latitude ?: 0.0,
                longitude = loc?.longitude ?: 0.0,
            )
        }
    }

    private fun flushMotionWindow(nowElapsed: Long) {
        if (accelBuffer.isEmpty()) return

        // Persist pending events in batches
        if (pendingEvents.size >= 5) {
            val batch = pendingEvents.toList()
            pendingEvents.clear()
            serviceScope.launch {
                tripRepository.saveEvents(batch)
            }
        }

        accelBuffer.clear()
        gyroBuffer.clear()
        windowStartMs = nowElapsed
    }

    // ── Stop & score ──────────────────────────────────────────────────────

    private fun stopTrip() {
        val endTime = System.currentTimeMillis()
        val durationMs = endTime - tripStartTimeMs

        // Flush remaining events
        val remaining = pendingEvents.toList()
        pendingEvents.clear()

        serviceScope.launch {
            if (remaining.isNotEmpty()) {
                tripRepository.saveEvents(remaining)
            }
            tripRepository.endTrip(currentTripId, endTime, totalDistanceMeters)

            // Retrieve all events for scoring (first emission after trip ends)
            val events = tripRepository.observeEventsForTrip(currentTripId).first()
            val isNight = isNightTime(tripStartTimeMs)
            val score = scoreCalculator.calculate(
                tripId = currentTripId,
                events = events,
                tripDurationMs = durationMs,
                isNight = isNight,
                signalConfidence = computeSignalConfidence(),
            )
            tripRepository.saveScore(score)
            stopSelf()
        }

        fusedLocationClient.removeLocationUpdates(locationCallback)
        sensorManager.unregisterListener(this)
    }

    private fun isNightTime(timestampMs: Long): Boolean {
        val hour = java.util.Calendar.getInstance().apply {
            timeInMillis = timestampMs
        }.get(java.util.Calendar.HOUR_OF_DAY)
        return hour < 6 || hour >= 22
    }

    // TODO: Track in-memory location-point count and GPS accuracy history to compute a
    //       meaningful confidence value. For now the MVP returns a fixed 100%.
    private fun computeSignalConfidence(): Float = 100f

    // ── Notification ──────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Trip Tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while a trip is being recorded"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TripTrackingService::class.java).apply { action = ACTION_STOP_TRIP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Recording trip")
            .setContentText("DrivePerf is tracking your drive")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openIntent)
            .addAction(0, "Stop", stopIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
