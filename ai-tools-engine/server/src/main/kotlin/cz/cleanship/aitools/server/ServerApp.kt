package cz.cleanship.aitools.server

import cz.cleanship.aitools.server.api.startBackend
import cz.cleanship.telemetry.Telemetry
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("Server")
val telemetry = Telemetry.create()

fun main() = runBlocking {
    telemetry.inSpan("server-main") {
        log.info("Starting AI Tools Backend Server...")
        startBackend(port = 8080)
    }
}
