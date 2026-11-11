package com.driveperf.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OutcomeTest {
    private val failure: Outcome<Int> = Outcome.Failure(DomainError.Storage.DiskFull)

    @Test
    fun mapTransformsSuccessAndKeepsFailure() {
        assertEquals(Outcome.Success(4), Outcome.Success(2).map { it * 2 })
        assertEquals<Outcome<Int>>(failure, failure.map { it * 2 })
    }

    @Test
    fun flatMapChainsOnlyAfterSuccess() {
        assertEquals(Outcome.Success("2"), Outcome.Success(2).flatMap { Outcome.Success(it.toString()) })
        assertEquals<Outcome<String>>(Outcome.Failure(DomainError.Storage.DiskFull), failure.flatMap { Outcome.Success(it.toString()) })
    }

    @Test
    fun getOrElseFallsBackOnFailure() {
        assertEquals(2, Outcome.Success(2).getOrElse { -1 })
        assertEquals(-1, failure.getOrElse { -1 })
    }

    @Test
    fun errorsCarryCodesOnly() {
        val denied = DomainError.Capture.PermissionDenied(PermissionKind.MOTION)
        assertEquals(PermissionKind.MOTION, denied.kind)
        assertEquals("trip", DomainError.Validation("trip", "too_short").field)
        assertEquals("c1", DomainError.Unexpected("c1").correlationId)
    }
}

class IdsTest {
    @Test
    fun idsAreLowerCaseUuidV7AndTimeOrdered() {
        val first = newId()
        val second = newId()
        assertTrue(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$").matches(first))
        assertTrue(first < second)
    }
}
