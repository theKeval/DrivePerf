package com.driveperf.platform.capture

import com.driveperf.domain.capture.ActivitySample
import com.driveperf.domain.capture.LocationSample
import com.driveperf.domain.capture.MotionSample

/**
 * The INTERIM R0 text format (DRIVE-72): one comma-separated line per sample, an empty field for null. `L` is a fix, `M`
 * a motion sample, `A` an activity change. The core's R0 codec (length-delimited protobuf chunks) replaces it; the
 * store, the sources and the clock stay.
 */
internal object R0Lines {
    const val HEADER = "# driveperf-r0-interim v0\n"

    fun locations(samples: List<LocationSample>): String = samples.joinToString("") { line(it) }

    fun motion(samples: List<MotionSample>): String = samples.joinToString("") { line(it) }

    fun activity(samples: List<ActivitySample>): String = samples.joinToString("") {
        "A,${it.tMonoNs},${it.wallMs},${it.type},${it.confidence}\n"
    }

    private fun line(s: LocationSample) = join(
        "L", s.tMonoNs, s.wallMs, s.lat, s.lng, s.altM, s.hAccM, s.vAccM, s.speedMps, s.speedAccMps, s.courseDeg, s.courseAccDeg,
    )

    private fun line(s: MotionSample): String {
        val a = s.userAccelG
        val g = s.gravityUp
        val r = s.rotRateDps
        val q = s.attitude
        return join("M", s.tMonoNs, a.x, a.y, a.z, g.x, g.y, g.z, r.x, r.y, r.z, q?.w, q?.x, q?.y, q?.z)
    }

    private fun join(vararg fields: Any?): String = fields.joinToString(",", postfix = "\n") { it?.toString().orEmpty() }
}
