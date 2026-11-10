package com.driveperf.platform.crash

/**
 * Finds coordinate-like pairs in text so crash reports never carry a location (ADR-007: `beforeSend` and
 * `beforeBreadcrumb` run the same scanner as analytics). A pair is two decimal numbers with at least four decimals.
 */
object CoordinateScrubber {
    private val coordinatePair = Regex("""-?\d{1,3}\.\d{4,}\s*[,;/ ]\s*-?\d{1,3}\.\d{4,}""")

    fun containsCoordinates(text: String?): Boolean = text != null && coordinatePair.containsMatchIn(text)

    /** Replaces every coordinate-like pair with a fixed marker. */
    fun scrub(text: String): String = coordinatePair.replace(text, "[location removed]")
}
