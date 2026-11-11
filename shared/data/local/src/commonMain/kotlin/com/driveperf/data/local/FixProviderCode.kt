package com.driveperf.data.local

import com.driveperf.domain.capture.FixProvider

/** `Gnss.provider` codes in R0 (tripfile.proto field 14, DRIVE-167). 0 is unknown, and so is any code from a newer writer. */
internal object FixProviderCode {
    fun of(p: FixProvider?): Int = when (p) {
        null -> 0
        FixProvider.GNSS -> 1
        FixProvider.FUSED -> 2
        FixProvider.NETWORK -> 3
        FixProvider.ACCESSORY -> 4
        FixProvider.SIMULATED -> 5
    }

    fun provider(code: Int): FixProvider? = when (code) {
        1 -> FixProvider.GNSS
        2 -> FixProvider.FUSED
        3 -> FixProvider.NETWORK
        4 -> FixProvider.ACCESSORY
        5 -> FixProvider.SIMULATED
        else -> null
    }
}
