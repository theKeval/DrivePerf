package com.driveperf.server

import com.driveperf.server.adapters.SystemClock
import com.driveperf.server.application.GetHealth
import com.driveperf.server.routes.healthRoutes
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

/** Composition root of the server: wires adapters into use cases and use cases into routes. */
public fun Application.module() {
    install(ContentNegotiation) { json() }
    val getHealth = GetHealth(SystemClock())
    routing { healthRoutes(getHealth) }
}
