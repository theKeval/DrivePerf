package com.driveperf.domain.analytics

// The analytics port (ADR-002 section 3, ADR-007, PRD F-ONB-08 and F-ANA-01). Features and the core call `Analytics`;
// the platform supplies an `AnalyticsSink` (Firebase Analytics once DRIVE-42 provides a project). Consent lives in
// `ConsentGatedAnalytics`, so no event can reach a sink before the user said yes, on either platform.

/**
 * One product event. Names are `snake_case` and carry no coordinates, addresses, route or car names, speeds, G values,
 * timestamps finer than the hour or free text (PRD 3.0.4). Batch 2 generates implementations from
 * `docs/analytics/events.yaml` (ADR-007), so this stays an interface; the port accepts only these types.
 */
public interface AnalyticsEvent {
    public val name: String

    /** Allow-listed, already bucketed values. The adapter adds the global properties (platform, app version, ...). */
    public val properties: Map<String, String>
}

/** What callers use. Every call is safe from any thread. */
public interface Analytics {
    /**
     * Records [event]: held in memory until the user answers S-ONB-13, then sent if they allowed usage statistics or
     * dropped if they did not. After the answer it is sent or dropped straight away.
     */
    public fun track(event: AnalyticsEvent)

    /**
     * Applies the user's answer to "Share usage statistics" (S-ONB-13, S-PRV-07). `true` turns collection on and sends
     * what was held; `false` turns it off and drops what was held. A later change takes effect straight away, within
     * the same app session (F-ONB-08 AC3).
     */
    public fun setUsageConsent(allowed: Boolean)

    /** "Reset analytics ID" (S-PRV-07): the vendor deletes the pseudonymous ID and starts a new one. */
    public fun resetId()
}

/**
 * The native side: a vendor SDK behind the port. It does no consent logic of its own; it only does what it is told.
 * With no vendor configured it is [NoOpAnalyticsSink].
 */
public interface AnalyticsSink {
    /**
     * Turns the vendor's collection on or off (Firebase `setAnalyticsCollectionEnabled`, consent mode
     * `analytics_storage`). Collection is disabled at build time and enabled here only after consent (ADR-007).
     */
    public fun setCollectionEnabled(enabled: Boolean)

    /** Sends [event]. Called only while collection is enabled. */
    public fun log(event: AnalyticsEvent)

    /** Deletes the vendor's analytics ID and data on the device (Firebase `resetAnalyticsData`). */
    public fun resetId()
}

/** The sink for builds with no analytics vendor configured (no Firebase project yet, DRIVE-42): sends nothing. */
public object NoOpAnalyticsSink : AnalyticsSink {
    override fun setCollectionEnabled(enabled: Boolean): Unit = Unit

    override fun log(event: AnalyticsEvent): Unit = Unit

    override fun resetId(): Unit = Unit
}
