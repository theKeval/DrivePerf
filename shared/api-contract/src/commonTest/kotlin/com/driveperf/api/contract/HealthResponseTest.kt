package com.driveperf.api.contract

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthResponseTest {
    @Test
    fun matchesTheOpenApiExample() {
        assertEquals("""{"status":"ok"}""", Json.encodeToString(HealthResponse.serializer(), HealthResponse("ok")))
    }
}
