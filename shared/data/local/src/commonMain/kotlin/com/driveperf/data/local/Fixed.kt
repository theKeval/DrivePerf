package com.driveperf.data.local

import kotlin.math.roundToLong

/** Quantisation, delta coding and length-delimited framing for [TripFileCodec]. */
internal object Fixed {
    const val ABSENT: Int = Int.MIN_VALUE
    const val NS_PER_MS: Long = 1_000_000L
    private const val MAX_VARINT_SHIFT = 35



    fun ms(ns: Long): Long = ns.floorDiv(NS_PER_MS)

    fun quant(x: Double, unit: Double): Int = (x / unit).roundToLong().toInt()

    fun quantN(x: Double?, unit: Double): Int = x?.let { quant(it, unit) } ?: ABSENT

    fun dequantN(v: Int, unit: Double): Double? = if (v == ABSENT) null else v * unit

    /** First value absolute, then differences; Int arithmetic wraps, so decoding with [undelta] is exact. */
    fun delta(v: List<Int>): List<Int> = v.mapIndexed { i, x -> if (i == 0) x else x - v[i - 1] }

    fun undelta(d: List<Int>): IntArray {
        val out = IntArray(d.size)
        for (i in d.indices) out[i] = if (i == 0) d[0] else out[i - 1] + d[i]
        return out
    }

    fun deltaLong(v: List<Long>, base: Long): List<Long> = v.mapIndexed { i, x -> x - if (i == 0) base else v[i - 1] }

    fun undeltaLong(d: List<Long>, base: Long): LongArray {
        val out = LongArray(d.size)
        for (i in d.indices) out[i] = (if (i == 0) base else out[i - 1]) + d[i]
        return out
    }

    fun delimited(payload: ByteArray): ByteArray = varint(payload.size.toLong()) + payload

    fun varint(value: Long): ByteArray {
        val out = mutableListOf<Byte>()
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out += ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        out += v.toByte()
        return out.toByteArray()
    }

    /** Reads `varint length + payload` frames; returns null at the end or on a truncated frame. */
    class FrameReader(private val bytes: ByteArray, private var pos: Int) {
        fun next(): ByteArray? {
            var len = 0L
            var shift = 0
            var complete = false
            while (pos < bytes.size && shift < MAX_VARINT_SHIFT && !complete) {
                val b = bytes[pos++].toInt()
                len = len or ((b and 0x7F).toLong() shl shift)
                shift += 7
                complete = b and 0x80 == 0
            }
            val end = pos + len
            if (!complete || end > bytes.size) return null
            return bytes.copyOfRange(pos, end.toInt()).also { pos = end.toInt() }
        }
    }

}
