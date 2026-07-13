package cz.cleanship.aitools.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import java.io.FileNotFoundException
import java.nio.file.Paths

class AiToolsCli(
    private val runner: ToolsApplicationRunner = DefaultToolsApplicationRunner(),
) : CliktCommand(name = "ai-tools") {
    private val workingDir by option(
        "--working-dir",
        help = "Root directory containing config.yml (and optional config.local.yml).",
    ).path(
        canBeFile = false,
        mustExist = true,
    ).default(Paths.get("."))

    override fun run() {
        try {
            runner.run(workingDir.toFile())
        } catch (ex: FileNotFoundException) {
            throw CliktError(ex.message ?: "Missing config.yml in ${workingDir.toAbsolutePath()}", ex)
        }
    }
}

fun main(args: Array<String>) = AiToolsCli().main(args)
