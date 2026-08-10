package cz.cleanship.aitools.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import cz.cleanship.aitools.engine.DeployDirectoryResolvingException
import cz.cleanship.aitools.engine.ExportFailedException
import cz.cleanship.aitools.engine.env.VariableSubstitutionException
import cz.cleanship.aitools.engine.services.DuplicateManifestIdException
import cz.cleanship.aitools.engine.services.ManifestLoadingException
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
     * Where a `user.yml` deploys to. It defaults to the home of whoever runs the command, which is the only home a
     * deploy is meant to reach; the option exists so that a run can be pointed at a directory of its own instead -
     * a dry run into a scratch directory, or a test that must never write into a real home.
     */
    private val userHome by option(
        "--user-home",
        help = "Home directory the user deployments are written under. Defaults to the home of the current user.",
    ).path(
        canBeFile = false,
    ).default(Paths.get(System.getProperty("user.home")))

    /**
     * Runs the export, translating the failures a manifest author can actually fix into a [CliktError] so the
     * process reports them on stderr and exits non-zero instead of reporting success.
     */
    override fun run() {
        try {
            runner.run(workingDir.toFile(), userHome.toFile())
        } catch (ex: FileNotFoundException) {
            throw CliktError(ex.message ?: "Missing config.yml in ${workingDir.toAbsolutePath()}", ex)
        } catch (ex: ExportFailedException) {
            throw CliktError(ex.message, ex)
        } catch (ex: DuplicateManifestIdException) {
            throw CliktError(ex.message, ex)
        } catch (ex: ManifestLoadingException) {
            throw CliktError(ex.message, ex)
        } catch (ex: VariableSubstitutionException) {
            throw CliktError(ex.message, ex)
        } catch (ex: DeployDirectoryResolvingException) {
            throw CliktError(ex.message, ex)
        }
    }
}

fun main(args: Array<String>) = AiToolsCli().main(args)
