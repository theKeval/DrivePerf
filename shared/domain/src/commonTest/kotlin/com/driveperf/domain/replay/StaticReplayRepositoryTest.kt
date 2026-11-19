package com.driveperf.domain.replay

import com.driveperf.domain.trip.BoundingBox
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertSame

class StaticReplayRepositoryTest {
    private val replay =
        Replay("fixture", BoundingBox(0.0, 0.0, 0.0, 0.0), 10.0, emptyList(), false, listOf(ReplayPoint(0.0, 0.0, 0.0, null)))

    @Test
    fun answersOnlyForItsOwnTrip() = runTest {
        assertSame(replay, StaticReplayRepository(replay).replay("fixture"))
        assertNull(StaticReplayRepository(replay).replay("other"))
        assertNull(StaticReplayRepository(null).replay("fixture"))
    }
}
