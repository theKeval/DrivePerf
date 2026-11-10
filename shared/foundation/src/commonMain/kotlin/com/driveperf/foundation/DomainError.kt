package com.driveperf.foundation

/**
 * Expected failures, by family (ADR-002 section 7). They carry codes and enums only, never free text that could hold
 * a place, a coordinate or a speed (data-model.md P10).
 */
public sealed interface DomainError {
    /** Local storage failures. */
    public sealed interface Storage : DomainError {
        /** Less free space than the operation needs (EC-STO). */
        public data object DiskFull : Storage

        /** Protected data is not readable yet, for example before the first unlock after a reboot (EC-RBT). */
        public data object Unavailable : Storage

        /** Stored data could not be decoded. */
        public data object Corrupt : Storage
    }

    /** Capture failures. */
    public sealed interface Capture : DomainError {
        /** The user has not granted the permission of [kind]. */
        public data class PermissionDenied(val kind: PermissionKind) : Capture

        /** The device lacks a sensor the operation needs. */
        public data object SensorUnavailable : Capture
    }

    /** Input that breaks a rule. [field] and [reason] are code keys, not user text. */
    public data class Validation(val field: String, val reason: String) : DomainError

    /** Anything else; [correlationId] links to a crash or log entry without carrying data. */
    public data class Unexpected(val correlationId: String) : DomainError
}

/** Permissions capture depends on. */
public enum class PermissionKind { LOCATION, BACKGROUND_LOCATION, MOTION, NOTIFICATIONS }
