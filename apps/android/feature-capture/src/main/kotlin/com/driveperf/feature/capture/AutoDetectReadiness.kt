package com.driveperf.feature.capture

import androidx.annotation.StringRes

/** The permissions automatic trips depend on, as the app reads them from the system (it can change in Settings). */
data class CapturePermissions(
    /** Some location, precise or approximate. */
    val location: Boolean,
    /** Precise location (`ACCESS_FINE_LOCATION`), not approximate only. */
    val precise: Boolean,
    /** "Allow all the time" (`ACCESS_BACKGROUND_LOCATION`). */
    val background: Boolean,
    /** Physical activity (`ACTIVITY_RECOGNITION`). */
    val activity: Boolean,
) {
    companion object {
        val ALL = CapturePermissions(location = true, precise = true, background = true, activity = true)
    }
}

/** Where a reason is fixed: the system permission dialog when Android still shows it, otherwise DrivePerf's App info. */
enum class AttentionFix { Location, Background, Activity }

/**
 * What automatic trips need that the user can change, most important first (F-CAP-02, S-HOME-01, S-HOME-02). Mirrors iOS
 * `AttentionReason` in Android wording; [ActivityUpdatesFailed] is Android's own (Play services refused the registration).
 */
enum class AttentionReason(
    /** The toolbar's second line. */
    @StringRes val shortText: Int,
    /** The banner's and the sheet's one-line explanation. */
    @StringRes val explanation: Int,
    /** How the check row reads while this reason holds. */
    @StringRes val checkValue: Int,
    /** Null when Settings cannot fix it. */
    val fix: AttentionFix?,
    /** Location lost: automatic trips cannot start at all (critical colour on the banner). */
    val critical: Boolean,
) {
    NoLocation(
        R.string.attention_no_location_short,
        R.string.attention_no_location_explanation,
        R.string.check_location_off,
        AttentionFix.Location,
        critical = true,
    ),
    NoBackgroundLocation(
        R.string.attention_while_using_short,
        R.string.attention_while_using_explanation,
        R.string.check_location_while_using,
        AttentionFix.Background,
        critical = true,
    ),
    NoActivityRecognition(
        R.string.attention_no_activity_short,
        R.string.attention_no_activity_explanation,
        R.string.check_activity_off,
        AttentionFix.Activity,
        critical = false,
    ),
    NoPreciseLocation(
        R.string.attention_no_precise_short,
        R.string.attention_no_precise_explanation,
        R.string.check_precise_off,
        AttentionFix.Location,
        critical = false,
    ),
    ActivityUpdatesFailed(
        R.string.attention_registration_short,
        R.string.attention_registration_explanation,
        R.string.check_activity_failed,
        fix = null,
        critical = false,
    ),
}

/** One row of the sheet's checks list. [reason] is null when the check passes. */
data class CaptureCheck(val kind: Kind, val reason: AttentionReason?) {
    val ok: Boolean get() = reason == null

    enum class Kind(@StringRes val title: Int, @StringRes val okValue: Int) {
        Location(R.string.check_location_title, R.string.check_location_ok),
        Precise(R.string.check_precise_title, R.string.check_precise_ok),
        Activity(R.string.check_activity_title, R.string.check_activity_ok),
    }
}

/**
 * Whether automatic trips are off (the user's choice), ready, opted in but blocked by [AutoDetectStatus.reasons], or
 * paused for a while by the user (F-CAP-15).
 */
enum class AutoDetectMode { Off, Ready, NeedsAttention, Paused }

/**
 * The one status every surface shows: the toolbar, the Home banner, the capture status sheet and Recording settings.
 * [lastCheckInMs] is the shared core's `RecorderStatus.lastCheckInWallMs`; [pausedUntilMs] its
 * `autoDetectPausedUntilWallMs` while [AutoDetectMode.Paused] (null: until the user turns it back on).
 */
data class AutoDetectStatus(
    val mode: AutoDetectMode,
    val reasons: List<AttentionReason>,
    val checks: List<CaptureCheck>,
    val lastCheckInMs: Long?,
    val pausedUntilMs: Long? = null,
)

/** Pure rules for "Auto-detect on / off / needs attention" (F-CAP-02), as iOS `AutoDetectReadiness`. */
object AutoDetectReadiness {
    /** Everything that needs fixing, most important first. Empty: automatic trips can run. */
    fun reasons(permissions: CapturePermissions): List<AttentionReason> = buildList {
        when {
            !permissions.location -> add(AttentionReason.NoLocation)
            !permissions.background -> add(AttentionReason.NoBackgroundLocation)
        }
        if (!permissions.activity) add(AttentionReason.NoActivityRecognition)
        // Without location there is nothing to be precise about; the first reason already covers it.
        if (permissions.location && !permissions.precise) add(AttentionReason.NoPreciseLocation)
    }

    /**
     * The status for [optedIn] (the user's Automatic trips choice) and [permissions]. [activityUpdatesFailed] is true when
     * Play services refused the activity registration although every permission is granted. [paused] is the shared
     * core's pause (F-CAP-15), until [pausedUntilMs]: it shows over a missing permission, as the core's health does, and
     * the checks still list what to fix.
     */
    @Suppress("LongParameterList") // the facts the one status is made of
    fun status(
        optedIn: Boolean,
        permissions: CapturePermissions,
        activityUpdatesFailed: Boolean = false,
        lastCheckInMs: Long? = null,
        paused: Boolean = false,
        pausedUntilMs: Long? = null,
    ): AutoDetectStatus {
        val permissionReasons = reasons(permissions)
        val reasons = if (permissionReasons.isEmpty() && activityUpdatesFailed) {
            listOf(AttentionReason.ActivityUpdatesFailed)
        } else {
            permissionReasons
        }
        val mode = when {
            !optedIn -> AutoDetectMode.Off
            paused -> AutoDetectMode.Paused
            reasons.isEmpty() -> AutoDetectMode.Ready
            else -> AutoDetectMode.NeedsAttention
        }
        val until = pausedUntilMs.takeIf { mode == AutoDetectMode.Paused }
        return AutoDetectStatus(mode, if (optedIn) reasons else emptyList(), checks(reasons), lastCheckInMs, until)
    }

    /** The checks list: failing rows first, then the passing ones, each in [CaptureCheck.Kind] order. */
    fun checks(reasons: List<AttentionReason>): List<CaptureCheck> {
        fun reasonFor(kind: CaptureCheck.Kind) = reasons.firstOrNull { kindOf(it) == kind }
        val all = CaptureCheck.Kind.entries.mapNotNull { kind ->
            // Precise location means nothing without location: the location row already says what to fix.
            if (kind == CaptureCheck.Kind.Precise && AttentionReason.NoLocation in reasons) return@mapNotNull null
            CaptureCheck(kind, reasonFor(kind))
        }
        return all.filterNot { it.ok } + all.filter { it.ok }
    }

    private fun kindOf(reason: AttentionReason): CaptureCheck.Kind = when (reason) {
        AttentionReason.NoLocation, AttentionReason.NoBackgroundLocation -> CaptureCheck.Kind.Location
        AttentionReason.NoPreciseLocation -> CaptureCheck.Kind.Precise
        AttentionReason.NoActivityRecognition, AttentionReason.ActivityUpdatesFailed -> CaptureCheck.Kind.Activity
    }
}
