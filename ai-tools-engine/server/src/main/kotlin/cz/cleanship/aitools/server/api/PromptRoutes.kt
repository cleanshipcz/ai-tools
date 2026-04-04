package cz.cleanship.aitools.server.api

import cz.cleanship.aitools.server.services.FillRequest
import cz.cleanship.aitools.server.services.FillResponse
import cz.cleanship.aitools.server.services.PromptService
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.promptRoutes(promptService: PromptService) {
    route("/api/prompts") {
        get {
            call.respond(promptService.listPrompts())
        }

        get("/{id}") {
            val id = call.parameters["id"] ?: return@get call.respond(HttpStatusCode.BadRequest)
            val prompt = promptService.getPrompt(id)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Prompt not found: $id"))
            call.respond(prompt)
        }

        post("/{id}/fill") {
            val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
            val request = call.receive<FillRequest>()
            val content = promptService.fillPrompt(id, request.variables)
                ?: return@post call.respond(HttpStatusCode.NotFound, mapOf("error" to "Prompt not found: $id"))
            call.respond(FillResponse(content))
        }
    }
}
