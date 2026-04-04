package cz.cleanship.aitools.server.api

import cz.cleanship.aitools.server.services.PromptService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("KtorConfig")

fun Application.module(promptService: PromptService) {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            },
        )
    }

    install(StatusPages) {
        exception<BadRequestException> { call, cause ->
            logger.warn("Bad request: {}", cause.message)
            call.respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Bad request"),
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                mapOf("error" to "Internal server error"),
            )
        }
    }

    routing {
        get("/health") {
            call.respond(mapOf("status" to "UP"))
        }

        promptRoutes(promptService)

        staticResources("/", "static", "index.html")
    }
}

fun startBackend(port: Int = 8080, promptService: PromptService) {
    embeddedServer(
        Netty,
        port = port,
        host = "0.0.0.0",
        module = { module(promptService) },
    ).start(wait = true)
}
