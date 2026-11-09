package com.driveperf.server.routes

import com.driveperf.api.contract.HealthResponse
import com.driveperf.server.application.GetHealth
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/** `GET /healthz` (backend/openapi/v1.yaml). */
internal fun Route.healthRoutes(getHealth: GetHealth) {
    get("/healthz") {
        call.respond(HealthResponse(status = getHealth().status))
    }
}
