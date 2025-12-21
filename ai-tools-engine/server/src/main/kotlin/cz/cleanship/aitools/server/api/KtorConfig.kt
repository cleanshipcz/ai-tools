package cz.cleanship.aitools.server.api

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

fun Application.module() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            },
        )
    }

    routing {
        get("/") {
            call.respond(mapOf("status" to "AI Tools Backend is running", "version" to "1.0.0-PROD"))
        }

        get("/health") {
            call.respond(mapOf("status" to "UP"))
        }
    }
}

fun startBackend(port: Int = 8080) {
    embeddedServer(Netty, port = port, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}
