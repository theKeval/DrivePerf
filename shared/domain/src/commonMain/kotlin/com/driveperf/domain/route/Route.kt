package com.driveperf.domain.route

/** A route is a candidate until named or run 3 times (§7.4). */
public enum class RouteState { CANDIDATE, ROUTE }

/** Name presets (F-RTE-01). */
public enum class RoutePreset { COMMUTE, GYM, SCHOOL_RUN, CUSTOM }

/** Who linked the trip to the route. Auto-matching never overrides [USER]. */
public enum class MatchSource { AUTO, USER }

/** Why a run cannot set a record, checked in this order (§7.5). */
public enum class IneligibleReason { PASSENGER, QUALITY, EXCLUSIONS, PACE_BAND }

/** A route (data-model.md §1.12). Direction is intrinsic: A→B and B→A are different routes. */
public data class Route(
    val id: String,
    val name: String?,
    val namePreset: RoutePreset?,
    val state: RouteState,
    val fromLabel: String?,
    val toLabel: String?,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val clusterRadiusM: Double,
    val referenceTripId: String,
    val distanceM: Double,
    val linkedRouteId: String? = null,
    val isActive: Boolean = true,
    val mergedIntoRouteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/** A trip's link to a route (data-model.md §1.13). */
public data class RouteMatch(
    val tripId: String,
    val routeId: String,
    val similarity: Double,
    val source: MatchSource,
    val eligibleForRecord: Boolean,
    val ineligibleReason: IneligibleReason?,
)

/** One entry of a route's record history. */
public data class RecordEntry(val tripId: String, val smoothness: Int, val setAt: Long)

/** The route record (data-model.md §1.14): Smoothness only, never time or speed. Derived, never synced. */
public data class PersonalBest(
    val routeId: String,
    val tripId: String,
    val smoothness: Int,
    val setAt: Long,
    val history: List<RecordEntry>,
)
