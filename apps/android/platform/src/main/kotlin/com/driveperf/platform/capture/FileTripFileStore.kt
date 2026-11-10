package com.driveperf.platform.capture

import com.driveperf.platform.ports.ActivityEvent
import com.driveperf.platform.ports.LocationSample
import com.driveperf.platform.ports.MotionSample
import com.driveperf.platform.ports.TripFileStore
import com.driveperf.platform.ports.TripFileWriter
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

/**
 * [TripFileStore] that writes one append-only text file per trip under [directory] (app-private storage).
 *
 * INTERIM format (DRIVE-72): one line per sample, comma separated, empty field for null. DRIVE-62's R0 codec replaces
 * it. `L` = location, `M` = motion, `A` = activity transition.
 */
class FileTripFileStore(private val directory: File) : TripFileStore {
    override fun open(tripId: String): TripFileWriter {
        directory.mkdirs()
        return Writer(File(directory, fileName(tripId)))
    }

    override fun delete(tripId: String) {
        File(directory, fileName(tripId)).delete()
    }

    private fun fileName(tripId: String) = "$tripId.r0"

    private class Writer(file: File) : TripFileWriter {
        private val stream = FileOutputStream(file, true)
        private val out = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8))

        init {
            if (file.length() == 0L) out.write("# driveperf-r0-interim v0\n")
        }

        @Synchronized
        override fun append(sample: LocationSample) {
            out.write(
                listOf(
                    "L", sample.tMonoNs, sample.wallMs, sample.lat, sample.lng, sample.altM, sample.hAccM, sample.vAccM,
                    sample.speedMps, sample.speedAccMps, sample.courseDeg, sample.courseAccDeg,
                ).joinToString(",") { it?.toString().orEmpty() },
            )
            out.write("\n")
        }

        @Synchronized
        override fun append(sample: MotionSample) {
            val a = sample.userAccelG
            val g = sample.gravityUp
            val r = sample.rotRateDps
            val q = sample.attitude
            out.write(
                listOf(
                    "M", sample.tMonoNs, a.x, a.y, a.z, g.x, g.y, g.z, r.x, r.y, r.z, q?.w, q?.x, q?.y, q?.z,
                ).joinToString(",") { it?.toString().orEmpty() },
            )
            out.write("\n")
        }

        @Synchronized
        override fun append(event: ActivityEvent) {
            out.write("A,${event.tMonoNs},${event.kind},${event.transition}\n")
        }

        @Synchronized
        override fun flush() {
            out.flush()
            stream.fd.sync() // the data survives a process kill or a battery pull, not just a clean exit
        }

        @Synchronized
        override fun close() {
            out.flush()
            stream.fd.sync()
            out.close()
        }
    }
}
