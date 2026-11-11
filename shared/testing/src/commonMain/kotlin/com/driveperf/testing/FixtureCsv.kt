package com.driveperf.testing

import com.driveperf.domain.capture.ActivityConfidence
import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.ActivityType
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample
import com.driveperf.domain.capture.Vec3
import kotlin.math.abs
import kotlin.math.round

/**
 * The §8.2 fixture format (pipeline-and-scoring.md): CSV whose first line is exactly `# privacy-trimmed`, `#` header
 * lines, one column-header line per row type, then rows `G` (GNSS), `M` (device-frame IMU), `A` (activity). Times are
 * milliseconds from `t = 0`. Empty means null.
 */
public object FixtureCsv {
    public const val PRIVACY_HEADER: String = "# privacy-trimmed"
    private const val G_HEADER = "G,t_ms,lat,lng,alt_m,h_acc_m,v_acc_m,speed_mps,speed_acc_mps,course_deg,course_acc_deg"
    private const val M_HEADER = "M,t_ms,ax,ay,az,gx,gy,gz,rx,ry,rz,qw,qx,qy,qz"
    private const val V_HEADER = "V,t_ms,lon_g,lat_g,vert_g,yaw_dps,quality"
    private const val A_HEADER = "A,t_ms,class,confidence"
    private const val X_HEADER = "X,t_ms,mark,detail"
    private const val NS_PER_MS = 1_000_000L

    /** Metadata lines after the privacy header. */
    public data class Meta(val id: String, val note: String, val imuHz: Int = 50)

    /** Parsed rows on the monotonic base [SyntheticCapture.START_MONO_NS] and wall base [SyntheticCapture.WALL0]. */
    public data class Parsed(
        val meta: Map<String, String>,
        val fixes: List<LocationSample>,
        val motion: List<MotionSample>,
        val activity: List<ActivitySample>,
    )

    public fun write(meta: Meta, streams: SyntheticCapture.Streams): String = buildString {
        appendLine(PRIVACY_HEADER)
        appendLine("# dpfx: 1")
        appendLine("# id: ${meta.id}")
        appendLine("# source: synthetic")
        appendLine("# level: R0")
        appendLine("# platform: none")
        appendLine("# imu_hz: ${meta.imuHz}")
        appendLine("# note: ${meta.note}")
        for (h in listOf(G_HEADER, M_HEADER, V_HEADER, A_HEADER, X_HEADER)) appendLine(h)
        for (f in streams.fixes) {
            appendLine(
                listOf(
                    "G", ms(f.tMonoNs), fixed(f.lat, 7), fixed(f.lng, 7), opt(f.altM, 1), opt(f.hAccM, 1), opt(f.vAccM, 1),
                    opt(f.speedMps, 2), opt(f.speedAccMps, 2), opt(f.courseDeg, 1), opt(f.courseAccDeg, 1),
                ).joinToString(","),
            )
        }
        for (m in streams.motion) {
            val row = listOf("M", ms(m.tMonoNs)) + vec(m.userAccelG, 4) + vec(m.gravityUp, 4) + vec(m.rotRateDps, 3) +
                listOf("", "", "", "")
            appendLine(row.joinToString(","))
        }
        for (a in streams.activity) appendLine("A,${ms(a.tMonoNs)},${a.type.name},${a.confidence.name}")
    }

    public fun parse(text: String): Parsed {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        require(lines.firstOrNull() == PRIVACY_HEADER) { "line 1 must be exactly '$PRIVACY_HEADER'" }
        val meta = lines.filter { it.startsWith("# ") && it.contains(": ") }
            .associate { it.removePrefix("# ").substringBefore(": ") to it.substringAfter(": ") }
        val headers = setOf(G_HEADER, M_HEADER, V_HEADER, A_HEADER, X_HEADER)
        val fixes = mutableListOf<LocationSample>()
        val motion = mutableListOf<MotionSample>()
        val activity = mutableListOf<ActivitySample>()
        for (line in lines) {
            if (line.startsWith("#") || line in headers) continue
            val c = line.split(',')
            when (c[0]) {
                "G" -> fixes += LocationSample(
                    mono(c[1]), wall(c[1]), c[2].toDouble(), c[3].toDouble(), c[4].d(), c[5].d(), c[6].d(), c[7].d(), c[8].d(),
                    c[9].d(), c[10].d(),
                )
                "M" -> motion += MotionSample(
                    mono(c[1]), Vec3(c[2].toDouble(), c[3].toDouble(), c[4].toDouble()),
                    Vec3(c[5].toDouble(), c[6].toDouble(), c[7].toDouble()), Vec3(c[8].toDouble(), c[9].toDouble(), c[10].toDouble()), null,
                )
                "A" -> activity += ActivitySample(mono(c[1]), wall(c[1]), ActivityType.valueOf(c[2]), ActivityConfidence.valueOf(c[3]))
                else -> Unit
            }
        }
        return Parsed(meta, fixes, motion, activity)
    }

    private fun mono(tMs: String): Long = SyntheticCapture.START_MONO_NS + tMs.toLong() * NS_PER_MS

    private fun wall(tMs: String): Long = SyntheticCapture.WALL0 + tMs.toLong()

    private fun String.d(): Double? = if (isEmpty()) null else toDouble()

    private fun ms(tMonoNs: Long): String = ((tMonoNs - SyntheticCapture.START_MONO_NS) / NS_PER_MS).toString()

    private fun vec(v: Vec3, decimals: Int): List<String> = listOf(fixed(v.x, decimals), fixed(v.y, decimals), fixed(v.z, decimals))

    private fun opt(x: Double?, decimals: Int): String = x?.let { fixed(it, decimals) } ?: ""

    /** Exact fixed-point text of [x] rounded to [decimals] places, without platform formatting. */
    public fun fixed(x: Double, decimals: Int): String {
        var scale = 1L
        repeat(decimals) { scale *= TEN }
        val n = round(x * scale).toLong()
        val sign = if (n < 0) "-" else ""
        val a = abs(n)
        val whole = a / scale
        val frac = (a % scale).toString().padStart(decimals, '0')
        return if (decimals == 0) "$sign$whole" else "$sign$whole.$frac"
    }

    private const val TEN = 10L
}
