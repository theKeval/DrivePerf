package com.driveperf.iosexport

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * Bulk copies between Kotlin `ByteArray` and `NSData`. The Objective-C export gives `KotlinByteArray` only per-byte
 * `get`/`set`, which is too slow for whole trip files (the core reads R0 at finalisation). Swift calls
 * `Bytes.shared.toData(bytes)` and `Bytes.shared.fromData(data)`.
 */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
public object Bytes {
    public fun toData(bytes: ByteArray): NSData =
        if (bytes.isEmpty()) {
            NSData()
        } else {
            bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        }

    public fun fromData(data: NSData): ByteArray {
        val size = data.length.toInt()
        val out = ByteArray(size)
        if (size > 0) out.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        return out
    }
}
