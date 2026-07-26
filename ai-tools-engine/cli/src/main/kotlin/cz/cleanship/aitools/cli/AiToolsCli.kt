package cz.cleanship.aitools.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import cz.cleanship.aitools.engine.ExportFailedException
import cz.cleanship.aitools.engine.services.DuplicateManifestIdException
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

    /**
     * Runs the export, translating the failures a manifest author can actually fix into a [CliktError] so the
     * process reports them on stderr and exits non-zero instead of reporting success.
     */
    override fun run() {
        try {
            runner.run(workingDir.toFile())
        } catch (ex: FileNotFoundException) {
            throw CliktError(ex.message ?: "Missing config.yml in ${workingDir.toAbsolutePath()}", ex)
        } catch (ex: ExportFailedException) {
            throw CliktError(ex.message, ex)
        } catch (ex: DuplicateManifestIdException) {
            throw CliktError(ex.message, ex)
        }
    }
}

fun main(args: Array<String>) = AiToolsCli().main(args)
