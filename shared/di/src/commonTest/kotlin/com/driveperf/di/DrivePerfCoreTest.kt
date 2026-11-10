package com.driveperf.di

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrivePerfCoreTest {
    @Test
    fun helloComesFromSharedCode() {
        val message = DrivePerfCore.hello()

        assertEquals("Hello DrivePerf", message.title)
        assertTrue(message.subtitle.startsWith("Shared core running on "))
    }
}
