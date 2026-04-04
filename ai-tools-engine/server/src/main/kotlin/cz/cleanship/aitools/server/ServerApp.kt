package cz.cleanship.aitools.server

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import cz.cleanship.aitools.engine.services.ConfigService
import cz.cleanship.aitools.engine.services.LoaderService
import cz.cleanship.aitools.server.api.startBackend
import cz.cleanship.aitools.server.services.PromptService
import cz.cleanship.telemetry.Telemetry
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.nio.file.Paths

private val log = LoggerFactory.getLogger("Server")
private val telemetry = Telemetry.create()

class ServerCommand : CliktCommand(name = "ai-tools-server") {
    private val workingDir by option(
        "--working-dir",
        help = "Root directory containing config.yml",
    ).path(
        canBeFile = false,
        mustExist = true,
    ).default(Paths.get("."))

    override fun run() = runBlocking {
        val promptService = telemetry.inSpan("server-init") {
            val configService = ConfigService()
            val loaderService = LoaderService()
            val config = configService.loadConfig(workingDir.toFile())
            val allManifests = loaderService.loadAll(config.locations)
            log.info("Loaded {} prompts", allManifests.prompts.size)
            PromptService(allManifests.prompts)
        }
        startBackend(port = 8080, promptService = promptService)
    }
}

fun main(args: Array<String>) = ServerCommand().main(args)
