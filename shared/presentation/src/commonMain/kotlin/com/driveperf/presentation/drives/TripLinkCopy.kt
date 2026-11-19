package com.driveperf.presentation.drives

import com.driveperf.presentation.evidence.EvidenceCopy

/**
 * The words of S-TRP-01's map hero and of the links from trip detail and S-SCO-01 to the evidence sheet and the tip
 * (DRIVE-55, DRIVE-87 AC2, DRIVE-102). Copy lives in the shared presenters (sprint 4 decision 3).
 */
public object TripLinkCopy {
    /** The chip over the map when the start of the route is estimated. */
    public const val ESTIMATED_START: String = "Estimated start"

    /** The tip card's link to the moment it is about (S-RPL-02). */
    public const val SEE_THE_MOMENT: String = EvidenceCopy.SEE_THE_MOMENT

    /** The accessibility action on an event row: "Hard braking at 8:14 a.m. … Opens the evidence." */
    public const val OPEN_EVIDENCE: String = "Opens the evidence"

    /** The accessibility action on the tip card (S-SCO-04). */
    public const val OPEN_TIP: String = "Opens the tip"

    /** The accessibility action on a contributor row with events, closed and open. */
    public const val SHOW_EVENTS: String = "Show events"
    public const val HIDE_EVENTS: String = "Hide events"
}
