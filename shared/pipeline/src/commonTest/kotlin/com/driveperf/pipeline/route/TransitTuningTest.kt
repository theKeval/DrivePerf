package com.driveperf.pipeline.route

import com.driveperf.domain.repository.ScoredTrip
import com.driveperf.domain.trip.Suspect
import com.driveperf.domain.trip.SuspectAnswer
import com.driveperf.pipeline.route.RouteTestData.lPath
import com.driveperf.pipeline.route.RouteTestData.trip
import com.driveperf.pipeline.signal.SignalConfig
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** F-CAP-04 AC4: no transit question on a path after 2 "No" answers on it. */
class TransitTuningTest {
    private val history = FakeRouteHistory(
        previews = mutableMapOf(
            "no-1" to lPath(), "no-2" to lPath(offsetEast = 30.0), "elsewhere" to lPath(offsetEast = 400.0),
            "yes-1" to lPath(), "unasked" to lPath(),
        ),
    )
    private val tuning = TransitTuning(history, SignalConfig())
    private val suspect = trip("new", 1_000L).copy(suspect = Suspect.TRANSIT)

    private fun past(id: String, answer: SuspectAnswer?, suspect: Suspect = Suspect.TRANSIT) =
        ScoredTrip(trip(id, 10L).copy(suspect = suspect, suspectAnswer = answer), null, null)

    @Test
    fun twoNoAnswersOnThisPathSuppressTheQuestion() = runTest {
        val recent = listOf(past("no-1", SuspectAnswer.NO), past("no-2", SuspectAnswer.NO))
        assertTrue(tuning.declinedHere(suspect, lPath(), recent))
        assertEquals(2, tuning.samePathCount(lPath(), recent.map { it.trip }))
    }

    @Test
    fun oneNoAnswerOrAnswersElsewhereDoNot() = runTest {
        assertFalse(tuning.declinedHere(suspect, lPath(), listOf(past("no-1", SuspectAnswer.NO))))
        val elsewhere = listOf(past("no-1", SuspectAnswer.NO), past("elsewhere", SuspectAnswer.NO))
        assertFalse(tuning.declinedHere(suspect, lPath(), elsewhere), "a parallel road 400 m away is another path")
        val others = listOf(past("no-1", SuspectAnswer.NO), past("yes-1", SuspectAnswer.YES), past("unasked", null))
        assertFalse(tuning.declinedHere(suspect, lPath(), others), "only No answers count")
    }

    @Test
    fun onlyTheTransitQuestionIsTuned() = runTest {
        val recent = listOf(past("no-1", SuspectAnswer.NO), past("no-2", SuspectAnswer.NO))
        assertFalse(tuning.declinedHere(suspect.copy(suspect = Suspect.CYCLING), lPath(), recent))
        assertFalse(tuning.declinedHere(suspect.copy(suspect = Suspect.NONE), lPath(), recent))
        val bikes = listOf(past("no-1", SuspectAnswer.NO, Suspect.CYCLING), past("no-2", SuspectAnswer.NO, Suspect.CYCLING))
        assertFalse(tuning.declinedHere(suspect, lPath(), bikes), "a No to the bike question says nothing about transit")
    }
}
