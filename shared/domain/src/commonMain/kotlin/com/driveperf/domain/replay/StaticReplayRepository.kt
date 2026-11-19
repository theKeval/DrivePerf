package com.driveperf.domain.replay

/**
 * A [ReplayRepository] that always answers with one prepared [replay], for previews and UI-test fixtures. Swift cannot
 * implement a Kotlin `suspend` port (ADR-002 section 3), so a fixture builds a synthetic [Replay] and wraps it here.
 * It answers only for the replay's own trip ID, and null for any other, as the real repository does for a missing trip.
 */
public class StaticReplayRepository(private val replay: Replay?) : ReplayRepository {
    override suspend fun replay(tripId: String): Replay? = replay?.takeIf { it.tripId == tripId }
}
