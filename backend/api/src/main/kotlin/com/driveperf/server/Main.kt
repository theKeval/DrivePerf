package com.driveperf.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/** Entry point of the Docker image. Cloud Run passes the port in `PORT`. */
public fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, module = { module() }).start(wait = true)
}
