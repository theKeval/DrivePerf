package com.driveperf.domain.hello

import kotlin.test.Test
import kotlin.test.assertEquals

class GetHelloMessageTest {
    @Test
    fun greetsAndNamesThePlatform() {
        val message = GetHelloMessage()("iOS 26.0")

        assertEquals(HelloMessage("Hello DrivePerf", "Shared core running on iOS 26.0"), message)
    }
}
