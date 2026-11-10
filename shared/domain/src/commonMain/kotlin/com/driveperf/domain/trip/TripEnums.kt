package com.driveperf.domain.trip

/** Persisted lifecycle (data-model.md §1.7). `CANDIDATE`, `PAUSED` and `FINISHING` all persist as [RECORDING]. */
public enum class TripStatus { RECORDING, FINALISED, INTERRUPTED, DISCARDED }

/** Who was driving. Only [DRIVER] trips are scored. A user correction changes it (F-HIS-02). */
public enum class TripKind { DRIVER, PASSENGER, TRANSIT, TAXI, NOT_DRIVING }

/** How the trip started. */
public enum class TripSource { AUTO, MANUAL }

/** Local part of day at the start (pipeline-and-scoring.md §3). */
public enum class PartOfDay { MORNING, MIDDAY, AFTERNOON, EVENING, NIGHT }

/** Road context from the share of fast fixes (§5.5). Higher contexts score stricter. */
public enum class DriveContext { CITY, MIXED, HIGHWAY }

/** What the core suspects a trip really was (§1.8). Only ever used to ask a question. */
public enum class Suspect { NONE, TRANSIT, CYCLING }

/** The user's answer to "Was this a bus, train or bike?". */
public enum class SuspectAnswer { YES, NO }

/** Which trip files still exist (F-HIS-03.4). */
public enum class RawState { FULL, ANALYSIS_ONLY, SUMMARY_ONLY }

/** Kind of a quality span (data-model.md §1.8). */
public enum class SegmentKind { ESTIMATED, GAP, EXCLUDED, PAUSED, INTERRUPTED }

/** Why a span exists (data-model.md §1.8 plus pipeline-and-scoring.md §0.8). */
public enum class SegmentReason {
    ESTIMATED_START, NO_GNSS, TUNNEL, PHONE_MOVED, APP_KILLED, LOW_BATTERY, THERMAL, USER_PAUSE,
    NOT_ALIGNED, LOW_IMU_RATE, IMU_GAP,
}

/** Data-quality badge (F-DQ-04, §2.9). Only [GOOD] and [USABLE] count for baselines and records. */
public enum class QualityBadge { GOOD, USABLE, LIMITED, UNSCORABLE }

/** The first rule of §2.9 that set the badge. */
public enum class QualityReason {
    NO_IMU, NO_PRECISE_LOCATION, NOT_ALIGNED, LOW_SCORED_SHARE, TOO_LITTLE_SCORED_TIME, LOW_GNSS, NO_GYRO, LOW_IMU_RATE,
}

/** Estimated phone placement (§2.4). `POCKET` and `BAG` are reserved: v1 cannot tell them apart. */
public enum class Placement { MOUNTED, CUPHOLDER, POCKET, BAG, UNKNOWN }

/** Why a trip ended without finalising normally. */
public enum class InterruptionReason { APP_KILLED, LOW_BATTERY }
