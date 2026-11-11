package com.driveperf.data.local

import com.driveperf.data.local.Fixed.NS_PER_MS
import com.driveperf.data.local.Fixed.dequantN
import com.driveperf.data.local.Fixed.undelta
import com.driveperf.data.local.Fixed.undeltaLong
import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureMark
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Quat
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.Vec3
import driveperf.tripfile.v1.Activity
import driveperf.tripfile.v1.Chunk
import driveperf.tripfile.v1.Gnss
import driveperf.tripfile.v1.Motion
import driveperf.tripfile.v1.RecordedAccel

/** Collects decoded chunks of one R0 file into a [CapturedTrip]. */
internal class TripFileDecoder(private val h: CaptureHeader, private val wall0: Long) {
    private val q = TripFileCodec.quantisation

    val fixes = mutableListOf<LocationSample>()
    val motion = mutableListOf<MotionSample>()
    val activity = mutableListOf<ActivitySample>()
    val recorded = mutableListOf<RecordedAccelSample>()
    val marks = mutableListOf<CaptureMark>()

    fun add(c: Chunk) {
        c.gnss?.let { addGnss(c.chunk_t0_ms, it) }
        c.motion?.let { addMotion(c.chunk_t0_ms, it) }
        c.activity?.let { addActivity(c.chunk_t0_ms, it) }
        c.recorded_accel?.let { addRecorded(c.chunk_t0_ms, it) }
        c.marks.forEach { m ->
            val type = CaptureMarkType.entries.firstOrNull { it.name == m.type.name }
            if (type != null) marks += CaptureMark(mono(m.t_ms), type, m.detail.ifEmpty { null })
        }
    }

    private fun mono(tMs: Long): Long = h.startMonoNs + tMs * NS_PER_MS

    private fun addGnss(t0: Long, g: Gnss) {
        val t = undeltaLong(g.dt_ms, t0)
        val lat = undelta(g.lat)
        val lng = undelta(g.lng)
        val alt = undelta(g.alt)
        val hAcc = undelta(g.h_acc)
        val vAcc = undelta(g.v_acc)
        val speed = undelta(g.speed)
        val speedAcc = undelta(g.speed_acc)
        val course = undelta(g.course)
        val courseAcc = undelta(g.course_acc)
        // Fields 13-14 (DRIVE-167) are absent from older files: those fixes have no age or provider.
        val age = undelta(g.age_ms)
        for (i in t.indices) {
            fixes += LocationSample(
                tMonoNs = mono(t[i]),
                wallMs = wall0 + t[i] + g.wall_ms_delta.getOrElse(i) { 0L },
                lat = lat[i] * q.latlng_deg_per_unit,
                lng = lng[i] * q.latlng_deg_per_unit,
                altM = dequantN(alt[i], q.alt_m_per_unit),
                hAccM = dequantN(hAcc[i], q.accuracy_m_per_unit),
                vAccM = dequantN(vAcc[i], q.accuracy_m_per_unit),
                speedMps = dequantN(speed[i], q.speed_mps_per_unit),
                speedAccMps = dequantN(speedAcc[i], q.speed_mps_per_unit),
                courseDeg = dequantN(course[i], q.course_deg_per_unit),
                courseAccDeg = dequantN(courseAcc[i], q.course_deg_per_unit),
                provider = g.provider.getOrNull(i)?.let(FixProviderCode::provider),
                ageMs = age.getOrNull(i)?.takeIf { it != Fixed.ABSENT }?.toLong(),
            )
        }
    }

    private fun addMotion(t0: Long, m: Motion) {
        val t = undeltaLong(m.dt_ms, t0)
        val ch = listOf(m.ax, m.ay, m.az, m.gx, m.gy, m.gz, m.rx, m.ry, m.rz, m.qw, m.qx, m.qy, m.qz).map { undelta(it) }
        val a = q.accel_g_per_unit
        val g = q.gyro_dps_per_unit
        val qu = q.quat_per_unit
        for (i in t.indices) {
            val w = dequantN(ch[9][i], qu)
            motion += MotionSample(
                tMonoNs = mono(t[i]),
                userAccelG = Vec3(ch[0][i] * a, ch[1][i] * a, ch[2][i] * a),
                gravityUp = Vec3(ch[3][i] * a, ch[4][i] * a, ch[5][i] * a),
                rotRateDps = Vec3(ch[6][i] * g, ch[7][i] * g, ch[8][i] * g),
                attitude = w?.let { Quat(it, ch[10][i] * qu, ch[11][i] * qu, ch[12][i] * qu) },
            )
        }
    }

    private fun addActivity(t0: Long, a: Activity) {
        val t = undeltaLong(a.dt_ms, t0)
        for (i in t.indices) {
            activity += ActivitySample(
                mono(t[i]),
                wall0 + t[i] + a.wall_ms_delta.getOrElse(i) { 0L },
                ActivityType.entries.getOrElse(a.type[i]) { ActivityType.UNKNOWN },
                ActivityConfidence.entries.getOrElse(a.confidence[i]) { ActivityConfidence.LOW },
            )
        }
    }

    private fun addRecorded(t0: Long, r: RecordedAccel) {
        val t = undeltaLong(r.dt_ms, t0)
        val x = undelta(r.ax)
        val y = undelta(r.ay)
        val z = undelta(r.az)
        val a = q.accel_g_per_unit
        for (i in t.indices) recorded += RecordedAccelSample(mono(t[i]), Vec3(x[i] * a, y[i] * a, z[i] * a))
    }

    fun result(): CapturedTrip = CapturedTrip(
        h,
        fixes.sortedBy { it.tMonoNs },
        motion.sortedBy { it.tMonoNs },
        activity.sortedBy { it.tMonoNs },
        recorded.sortedBy { it.tMonoNs },
        marks.sortedBy { it.tMonoNs },
    )
}

