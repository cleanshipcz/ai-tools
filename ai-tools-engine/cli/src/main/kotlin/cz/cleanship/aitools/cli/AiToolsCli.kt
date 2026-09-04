package cz.cleanship.aitools.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.check
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.path
import cz.cleanship.aitools.engine.DeployDirectoryResolvingException
import cz.cleanship.aitools.engine.ExportFailedException
import cz.cleanship.aitools.engine.env.VariableSubstitutionException
import cz.cleanship.aitools.engine.io.ArtifactPathException
import cz.cleanship.aitools.engine.io.resolveDeclaredPath
import cz.cleanship.aitools.engine.services.DuplicateManifestIdException
import cz.cleanship.aitools.engine.services.ManifestLoadingException
import cz.cleanship.aitools.engine.services.RetiredConfigKeyException
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
     * a trial run into a scratch directory, or a test that must never write into a real home.
     *
     * A relative value resolves against `--working-dir` - see [run] - which is the base every other declared path of
     * a run uses. An empty value is rejected rather than resolved: it would silently mean "the directory the shell
     * happens to be in", which is never what someone typing this option intends. A home that does not exist yet is
     * allowed, because a first deploy onto a fresh machine is legitimate; the engine says so in its log.
     */
    private val userHome by option(
        "--user-home",
        help = "Home directory the user deployments are written under. Relative to --working-dir. Defaults to the home of the current user.",
    ).path(
        canBeFile = false,
    ).default(Paths.get(System.getProperty("user.home")))
        .check("--user-home must name a directory, not an empty path") { it.toString().isNotEmpty() }

    /**
     * Validates the manifests without deploying them: the run loads, filters and renders everything a deploy does
     * and fails on exactly what a deploy fails on, but creates, deletes and modifies nothing on disk. It is the way
     * to check a manifest set before letting a deploy rewrite the projects and the home it reaches.
     */
    private val dryRun by option(
        "--dry-run",
        help = "Validate and render every manifest, reporting what would be written, without writing anything.",
    ).flag()

    /**
     * Runs the export, translating the failures a manifest author can actually fix into a [CliktError] so the
     * process reports them on stderr and exits non-zero instead of reporting success.
     */
    override fun run() {
        try {
            runner.run(workingDir.toFile(), workingDir.toFile().resolveDeclaredPath(userHome.toString()), dryRun)
        } catch (ex: FileNotFoundException) {
            throw failure(ex.message ?: "Missing config.yml in ${workingDir.toAbsolutePath()}", ex)
        } catch (ex: ExportFailedException) {
            throw failure(ex.message, ex)
        } catch (ex: DuplicateManifestIdException) {
            throw failure(ex.message, ex)
        } catch (ex: ManifestLoadingException) {
            throw failure(ex.message, ex)
        } catch (ex: VariableSubstitutionException) {
            throw failure(ex.message, ex)
        } catch (ex: DeployDirectoryResolvingException) {
            throw failure(ex.message, ex)
        } catch (ex: ArtifactPathException) {
            throw failure(ex.message, ex)
        } catch (ex: RetiredConfigKeyException) {
            throw failure(ex.message, ex)
        }
        if (dryRun) {
            echo(DRY_RUN_SUCCEEDED)
        }
    }

    /**
     * A failing dry run is still a dry run: its error reads exactly like the one a deploy reports, so the mode is
     * said once more above it, along with the one thing the reader most wants to know - that nothing was touched.
     */
    private fun failure(message: String?, cause: Exception): CliktError {
        val reported = if (dryRun) "$DRY_RUN_FAILED\n$message" else message
        return CliktError(reported, cause)
    }

    companion object {
        private const val DRY_RUN_SUCCEEDED = "Dry run finished: every manifest was validated and rendered, nothing was written."
        private const val DRY_RUN_FAILED = "Dry run failed: nothing was written."
    }
}

fun main(args: Array<String>) = AiToolsCli().main(args)
