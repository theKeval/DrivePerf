package com.driveperf.data.local

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.CaptureBatch
import com.driveperf.domain.capture.CaptureHeader
import com.driveperf.domain.capture.CaptureMark
import com.driveperf.domain.capture.CaptureMarkType
import com.driveperf.domain.capture.CapturedTrip
import com.driveperf.domain.capture.GeoPoint
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Parking
import com.driveperf.domain.capture.Quat
import com.driveperf.domain.capture.RecordedAccelSample
import com.driveperf.domain.capture.Vec3
import com.driveperf.domain.capture.WakeKind
import driveperf.tripfile.v1.Activity
import driveperf.tripfile.v1.Chunk
import driveperf.tripfile.v1.Gnss
import driveperf.tripfile.v1.Header
import driveperf.tripfile.v1.Mark
import driveperf.tripfile.v1.Motion
import driveperf.tripfile.v1.Quantisation
import driveperf.tripfile.v1.RecordedAccel
import com.driveperf.data.local.Fixed.FrameReader
import com.driveperf.data.local.Fixed.NS_PER_MS
import com.driveperf.data.local.Fixed.delimited
import com.driveperf.data.local.Fixed.delta
import com.driveperf.data.local.Fixed.deltaLong
import com.driveperf.data.local.Fixed.dequantN
import com.driveperf.data.local.Fixed.ms
import com.driveperf.data.local.Fixed.quant
import com.driveperf.data.local.Fixed.quantN
import com.driveperf.data.local.Fixed.undelta
import com.driveperf.data.local.Fixed.undeltaLong
import driveperf.tripfile.v1.Parking as ParkingProto

/**
 * Encodes and decodes R0 capture logs in the `tripfile.proto` format (data-model.md §2.4): magic `DPTF`, a
 * length-delimited header, then one length-delimited chunk per append. Values are quantised, then delta-encoded
 * per channel within a chunk with 32-bit wrapping; absent values use [ABSENT] before encoding.
 */
internal object TripFileCodec {
    const val FORMAT_VERSION: Int = 1
    private val MAGIC = byteArrayOf('D'.code.toByte(), 'P'.code.toByte(), 'T'.code.toByte(), 'F'.code.toByte())

    val quantisation: Quantisation = Quantisation(
        latlng_deg_per_unit = 1e-7,
        alt_m_per_unit = 0.1,
        speed_mps_per_unit = 0.01,
        accuracy_m_per_unit = 0.1,
        course_deg_per_unit = 0.1,
        accel_g_per_unit = 0.001,
        gyro_dps_per_unit = 0.01,
        quat_per_unit = 1e-4,
    )
    private val q = quantisation
    // ---- framing ----

    fun encodeHeader(h: CaptureHeader): ByteArray {
        val header = Header(
            format_version = FORMAT_VERSION,
            kind = Header.Kind.R0,
            trip_id = h.tripId,
            started_at_ms = h.startWallMs,
            tz_offset_min = h.tzOffsetMin,
            tz_id = h.tzId,
            motion_rate_hz = h.nominalImuHz.toFloat(),
            motion_frame = Header.Frame.DEVICE,
            q = q,
            started_at_mono_ns = h.startMonoNs,
            wake_kind = h.wakeKind.name,
            source_detail = h.sourceDetail.orEmpty(),
            parking = h.parking?.let {
                ParkingProto(quant(it.point.lat, q.latlng_deg_per_unit), quant(it.point.lng, q.latlng_deg_per_unit), it.wallMs)
            },
        )
        return MAGIC + delimited(Header.ADAPTER.encode(header))
    }

    fun encodeChunk(h: CaptureHeader, batch: CaptureBatch): ByteArray = delimited(Chunk.ADAPTER.encode(chunk(h, batch)))

    /** A decoded file and the length of its valid prefix: everything up to the end of the last whole chunk. */
    class Scan(val trip: CapturedTrip, val validBytes: Int)

    /** Decodes a file like [decode], also returning where the valid prefix ends, so a cut-off tail can be removed (§1.7). */
    fun scan(bytes: ByteArray): Scan? {
        val magicOk = bytes.size >= MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        val reader = FrameReader(bytes, MAGIC.size)
        val header = if (magicOk) reader.next()?.let { decodeOrNull { Header.ADAPTER.decode(it) } } else null
        if (header == null) return null
        val acc = TripFileDecoder(header.toCaptureHeader(), header.started_at_ms)
        var valid = reader.position
        while (true) {
            val chunk = reader.next()?.let { decodeOrNull { Chunk.ADAPTER.decode(it) } } ?: break
            acc.add(chunk)
            valid = reader.position
        }
        return Scan(acc.result(), valid)
    }

    private inline fun <T> decodeOrNull(block: () -> T): T? = try {
        block()
    } catch (expected: okio.IOException) {
        null
    } catch (expected: IllegalStateException) {
        null
    }

    private fun Header.toCaptureHeader(): CaptureHeader = CaptureHeader(
        tripId = trip_id,
        startMonoNs = started_at_mono_ns,
        startWallMs = started_at_ms,
        tzOffsetMin = tz_offset_min,
        tzId = tz_id,
        wakeKind = WakeKind.entries.firstOrNull { it.name == wake_kind } ?: WakeKind.RELAUNCH,
        sourceDetail = source_detail.ifEmpty { null },
        nominalImuHz = motion_rate_hz.toInt(),
        parking = parking?.let {
            val unit = quantisation.latlng_deg_per_unit
            Parking(GeoPoint(it.lat * unit, it.lng * unit), it.at_ms)
        },
    )

    // ---- chunk encoding ----

    private fun chunk(h: CaptureHeader, b: CaptureBatch): Chunk {
        val times = b.fixes.map { it.tMonoNs } + b.motion.map { it.tMonoNs } + b.activity.map { it.tMonoNs } +
            b.recordedAccel.map { it.tMonoNs } + b.marks.map { it.tMonoNs }
        val t0 = (times.minOrNull() ?: h.startMonoNs).let { ms(it - h.startMonoNs) }
        return Chunk(
            chunk_t0_ms = t0,
            gnss = if (b.fixes.isEmpty()) null else gnss(h, t0, b.fixes),
            motion = if (b.motion.isEmpty()) null else motion(h, t0, b.motion),
            marks = b.marks.map { Mark(ms(it.tMonoNs - h.startMonoNs), Mark.Type.valueOf(it.type.name), it.detail.orEmpty()) },
            activity = if (b.activity.isEmpty()) null else activity(h, t0, b.activity),
            recorded_accel = if (b.recordedAccel.isEmpty()) null else recorded(h, t0, b.recordedAccel),
        )
    }

    private fun gnss(h: CaptureHeader, t0: Long, f: List<LocationSample>): Gnss {
        val t = f.map { ms(it.tMonoNs - h.startMonoNs) }
        return Gnss(
            dt_ms = deltaLong(t, t0),
            wall_ms_delta = f.mapIndexed { i, s -> s.wallMs - (h.startWallMs + t[i]) },
            lat = delta(f.map { quant(it.lat, q.latlng_deg_per_unit) }),
            lng = delta(f.map { quant(it.lng, q.latlng_deg_per_unit) }),
            alt = delta(f.map { quantN(it.altM, q.alt_m_per_unit) }),
            h_acc = delta(f.map { quantN(it.hAccM, q.accuracy_m_per_unit) }),
            v_acc = delta(f.map { quantN(it.vAccM, q.accuracy_m_per_unit) }),
            speed = delta(f.map { quantN(it.speedMps, q.speed_mps_per_unit) }),
            speed_acc = delta(f.map { quantN(it.speedAccMps, q.speed_mps_per_unit) }),
            course = delta(f.map { quantN(it.courseDeg, q.course_deg_per_unit) }),
            course_acc = delta(f.map { quantN(it.courseAccDeg, q.course_deg_per_unit) }),
        )
    }

    private fun motion(h: CaptureHeader, t0: Long, m: List<MotionSample>): Motion {
        val a = q.accel_g_per_unit
        val g = q.gyro_dps_per_unit
        val qu = q.quat_per_unit
        return Motion(
            dt_ms = deltaLong(m.map { ms(it.tMonoNs - h.startMonoNs) }, t0),
            ax = delta(m.map { quant(it.userAccelG.x, a) }), ay = delta(m.map { quant(it.userAccelG.y, a) }),
            az = delta(m.map { quant(it.userAccelG.z, a) }),
            gx = delta(m.map { quant(it.gravityUp.x, a) }), gy = delta(m.map { quant(it.gravityUp.y, a) }),
            gz = delta(m.map { quant(it.gravityUp.z, a) }),
            rx = delta(m.map { quant(it.rotRateDps.x, g) }), ry = delta(m.map { quant(it.rotRateDps.y, g) }),
            rz = delta(m.map { quant(it.rotRateDps.z, g) }),
            qw = delta(m.map { quantN(it.attitude?.w, qu) }), qx = delta(m.map { quantN(it.attitude?.x, qu) }),
            qy = delta(m.map { quantN(it.attitude?.y, qu) }), qz = delta(m.map { quantN(it.attitude?.z, qu) }),
        )
    }

    private fun activity(h: CaptureHeader, t0: Long, s: List<ActivitySample>): Activity {
        val t = s.map { ms(it.tMonoNs - h.startMonoNs) }
        return Activity(
            dt_ms = deltaLong(t, t0),
            wall_ms_delta = s.mapIndexed { i, a -> a.wallMs - (h.startWallMs + t[i]) },
            type = s.map { it.type.ordinal },
            confidence = s.map { it.confidence.ordinal },
        )
    }

    private fun recorded(h: CaptureHeader, t0: Long, s: List<RecordedAccelSample>): RecordedAccel {
        val a = q.accel_g_per_unit
        return RecordedAccel(
            dt_ms = deltaLong(s.map { ms(it.tMonoNs - h.startMonoNs) }, t0),
            ax = delta(s.map { quant(it.accelG.x, a) }),
            ay = delta(s.map { quant(it.accelG.y, a) }),
            az = delta(s.map { quant(it.accelG.z, a) }),
        )
    }

    // ---- chunk decoding ----

}

/**
 * Decodes a whole file. A truncated or undecodable last chunk ends the read: everything before it is kept (§1.7).
 * Returns null when the magic or header is unreadable.
 */
internal fun TripFileCodec.decode(bytes: ByteArray): CapturedTrip? = scan(bytes)?.trip
