package cz.cleanship.aitools.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import cz.cleanship.telemetry.Telemetry
import kotlinx.coroutines.runBlocking

class AiToolsCli : CliktCommand(name = "ai-tools") {
    val name by option(help = "The name to greet").default("World")

    override fun run() = runBlocking {
        val telemetry = Telemetry.create()
        telemetry.inSpan("cli-run") {
            echo("Hello $name from AI Tools CLI!")
            echo("Engine logic will be integrated here.")
        }
    }
}

fun main(args: Array<String>) = AiToolsCli().main(args)
