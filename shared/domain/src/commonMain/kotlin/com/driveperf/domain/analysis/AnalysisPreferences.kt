package com.driveperf.domain.analysis

/** What S-ANL-01 shows, remembered per user (F-RPL-05 AC3–4): the timeline's channels and its x-axis. */
public data class AnalysisChoices(
    val channels: Set<AnalysisChannel> = AnalysisChannel.DEFAULTS,
    val axis: AnalysisAxis = AnalysisAxis.TIME,
)

/**
 * Keeps [AnalysisChoices] in the local profile (`profile.analysis_channels`). Implemented in `:shared:data:local`.
 * Kept apart from the display `Settings` so a settings save never resets the user's channels.
 */
public interface AnalysisPreferences {
    /** The stored choices, or the defaults when there are none or they cannot be read. */
    public suspend fun read(): AnalysisChoices

    /** Stores [choices]. False when they could not be written; the screen keeps them for this visit. */
    public suspend fun save(choices: AnalysisChoices): Boolean
}
