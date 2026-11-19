package com.driveperf.presentation.score

import com.driveperf.domain.route.RoutePreset
import com.driveperf.domain.score.Band
import com.driveperf.domain.score.ContributorId
import com.driveperf.domain.score.RouteName
import com.driveperf.domain.trip.DriveContext
import com.driveperf.domain.trip.EventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoreCopyTest {
    @Test
    fun everyReasonKeyHasCopyWithoutAPlaceName() {
        val reasons = ContributorId.entries.flatMap { id ->
            Band.entries.map { band -> "${id.name.lowercase()}.${band.name.lowercase()}" to ScoreCopy.reason(id,
                "${id.name.lowercase()}.${band.name.lowercase()}", 2) }
        }.toMap()
        assertEquals(
            mapOf(
                "braking.smoother" to "2 abrupt stops", "braking.usual" to "2 abrupt stops", "braking.rougher" to "2 abrupt stops",
                "acceleration.smoother" to "2 sharp starts", "acceleration.usual" to "2 sharp starts",
                "acceleration.rougher" to "2 sharp starts",
                "cornering.smoother" to "2 hard corners", "cornering.usual" to "2 hard corners", "cornering.rougher" to "2 hard corners",
                "consistency.smoother" to "Steadier speed than usual", "consistency.usual" to "Your usual rhythm",
                "consistency.rougher" to "More speed changes than usual",
            ),
            reasons,
        )
    }

    @Test
    fun reasonsCountOneOrNoEventsAndStillReadWhileCalibrating() {
        assertEquals("1 abrupt stop", ScoreCopy.reason(ContributorId.BRAKING, "braking.usual", 1))
        assertEquals("No sharp starts this drive", ScoreCopy.reason(ContributorId.ACCELERATION, "acceleration.smoother", 0))
        assertEquals("1 hard corner", ScoreCopy.reason(ContributorId.CORNERING, null, 1))
        assertEquals("No hard corners this drive", ScoreCopy.reason(ContributorId.CORNERING, null, 0))
        assertEquals("How steady your speed was", ScoreCopy.reason(ContributorId.CONSISTENCY, null, 0))
    }

    @Test
    fun bandsCarryAGlyphAndWords() {
        assertEquals(listOf("▲", "●", "▼"), Band.entries.map(ScoreCopy::bandGlyph))
        assertEquals(listOf("Smoother than usual", "Usual", "Rougher than usual"), Band.entries.map(ScoreCopy::bandText))
        assertEquals(listOf("Braking", "Acceleration", "Cornering", "Consistency"), ContributorId.entries.map(ScoreCopy::contributorLabel))
        assertEquals(listOf("Hard braking", "Hard acceleration", "Hard cornering"), EventType.entries.map(ScoreCopy::eventLabel))
        assertEquals(listOf("Braking", "Acceleration", "Cornering"), EventType.entries.map(ScoreCopy::eventCountLabel))
    }

    @Test
    fun comparisonsAreAgainstTheUsersOwnRouteOrDrivesOfTheSameKind() {
        assertEquals("+2 vs your usual on The Commute", ComparisonCopy.comparisonText(2, true, "The Commute", DriveContext.CITY))
        assertEquals("−3 vs your usual on this route", ComparisonCopy.comparisonText(-3, true, null, null))
        assertEquals("+4 vs your usual highway drives", ComparisonCopy.comparisonText(4, false, "The Commute", DriveContext.HIGHWAY))
        assertEquals("−1 vs your usual drives", ComparisonCopy.comparisonText(-1, false, null, null))
        assertEquals("Right on your usual", ComparisonCopy.comparisonText(0, false, null, DriveContext.MIXED))
        assertEquals("Calibrating your baseline · 2 of 3", ComparisonCopy.calibratingText(2, 3))
        assertEquals("3 below your usual city drives.", ComparisonCopy.spokenComparison(-3, false, null, DriveContext.CITY))
        assertEquals("2 above your usual on Gym.", ComparisonCopy.spokenComparison(2, true, "Gym", null))
        assertEquals("Right on your usual.", ComparisonCopy.spokenComparison(0, true, "Gym", null))
    }

    @Test
    fun routesAreNamedByTheUserOrTheirPreset() {
        assertEquals("Morning loop", ComparisonCopy.routeName(RouteName("r", "Morning loop", RoutePreset.GYM)))
        assertEquals("The Commute", ComparisonCopy.routeName(RouteName("r", " ", RoutePreset.COMMUTE)))
        assertEquals("Gym", ComparisonCopy.routeName(RouteName("r", null, RoutePreset.GYM)))
        assertEquals("School run", ComparisonCopy.routeName(RouteName("r", null, RoutePreset.SCHOOL_RUN)))
        assertNull(ComparisonCopy.routeName(RouteName("r", null, RoutePreset.CUSTOM)))
        assertNull(ComparisonCopy.routeName(RouteName("r", null, null)))
        assertNull(ComparisonCopy.routeName(null))
        assertEquals("+5", ComparisonCopy.signed(5))
        assertEquals("−5", ComparisonCopy.signed(-5))
        assertEquals("0", ComparisonCopy.signed(0))
    }
}
