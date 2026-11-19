package com.driveperf.domain.analysis

/**
 * A timeline lane of S-ANL-01 (F-RPL-05). [key] is what `profile.analysis_channels` stores and what
 * `analysis_channel_toggled {channel}` sends. [pro] channels are Pro (F-RPL-09.2).
 */
public enum class AnalysisChannel(public val key: String, public val pro: Boolean) {
    SPEED("speed", false),
    LON_G("lon_g", false),
    LAT_G("lat_g", false),
    ELEVATION("elevation", false),
    JERK("jerk", true),
    QUALITY("quality", true),
    ;

    public companion object {
        /** What a new user sees: speed, longitudinal G and lateral G (F-RPL-05 AC1; elevation is optional). */
        public val DEFAULTS: Set<AnalysisChannel> = setOf(SPEED, LON_G, LAT_G)

        public fun fromKey(key: String): AnalysisChannel? = entries.firstOrNull { it.key == key }
    }
}

/** The timeline's x-axis (F-RPL-05 AC3), remembered per user. [key] is the `analysis_axis` value. */
public enum class AnalysisAxis(public val key: String) {
    TIME("time"),
    DISTANCE("distance"),
    ;

    public companion object {
        public fun fromKey(key: String?): AnalysisAxis? = entries.firstOrNull { it.key == key }
    }
}
