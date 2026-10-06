package cz.cleanship.aitools.cli

import cz.cleanship.aitools.engine.ToolsEngine
import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.SecretsManagerKind
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.services.ConfigService
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.ToolFactory
import cz.cleanship.aitools.engine.tools.mcp.McpSecretsManager
import cz.cleanship.aitools.engine.tools.mcp.managerFor
import java.io.File

fun interface ToolsApplicationRunner {
    /**
     * @param userHome the home directory the user deployments of the run are written under - see
     * [ToolsEngine]
     * @param dryRun whether the run validates without writing anything - see [ToolsEngine]
     */
    fun run(workingDirectory: File, userHome: File, dryRun: Boolean)
}

class DefaultToolsApplicationRunner(
    private val configService: ConfigService = ConfigService(),
    private val toolAdapterFactory: ToolAdapterFactory = DefaultToolAdapterFactory,
    private val engineFactory: ToolsEngineFactory = DefaultToolsEngineFactory(),
) : ToolsApplicationRunner {
    override fun run(workingDirectory: File, userHome: File, dryRun: Boolean) {
        val config = configService.loadConfig(workingDirectory)
        // Both halves of a dry run are decided here: the adapters own the writes, the engine owns the deletions of
        // a replacing deploy and the wording of the run, and neither can stand in for the other.
        val toolAdapters = config.tools.map { toolAdapterFactory.create(it, dryRun) }
        engineFactory.create(toolAdapters, workingDirectory, config.variables, userHome, dryRun, config.secretsManager).process(config.locations)
    }
}

fun interface ToolAdapterFactory {
    /**
     * @param dryRun whether the adapter writes nothing and only reports what it would write - see
     * [ToolFactory.create]
     */
    fun create(toolType: ToolType, dryRun: Boolean): ToolAdapter
}

object DefaultToolAdapterFactory : ToolAdapterFactory {
    override fun create(toolType: ToolType, dryRun: Boolean): ToolAdapter = ToolFactory.create(toolType, dryRun)
}

fun interface ToolsEngineProcessor {
    fun process(locations: Locations)
}

/**
 * Builds the secrets manager of a run.
 */
fun interface SecretsManagerFactory {
    /**
     * Returns the secrets manager [kind] names for the repository [repository], which reads [environment] only, or `null` when the machine uses none.
     */
    fun create(kind: SecretsManagerKind, repository: File, environment: EnvironmentSource): McpSecretsManager?
}

fun interface ToolsEngineFactory {
    /**
     * @param workingDirectory the `--working-dir` of the run, which the engine resolves a relative
     * `deploy.directory` against
     * @param variables the variables the config files of the run declared, which the engine substitutes a
     * `deploy.directory` with before resolving it - the same ones the `locations.*` of that config were substituted
     * with, so that one name means one directory across the whole run
     * @param userHome the `--user-home` of the run, under which the adapters write the user scope of their tool
     * @param dryRun whether the run validates without writing anything - see [ToolsEngine]
     * @param secretsManager the secrets manager of the machine the config files name; a manager with a launcher finds it under [workingDirectory], and it reads the environment of [variables] only - see [cz.cleanship.aitools.engine.tools.mcp.managerFor]
     */
    // One parameter per setting of the run that the config files and the command line decide, as ToolsEngine takes them; bundling them would only move the list.
    @Suppress("LongParameterList")
    fun create(
        tools: List<ToolAdapter>,
        workingDirectory: File,
        variables: VariableResolver,
        userHome: File,
        dryRun: Boolean,
        secretsManager: SecretsManagerKind,
    ): ToolsEngineProcessor
}

/**
 * Builds the [ToolsEngine] of a run.
 *
 * @param secretsManagers builds the secrets manager of a [SecretsManagerKind] for a repository and the environment of the run - see [managerFor]
 */
class DefaultToolsEngineFactory(
    private val secretsManagers: SecretsManagerFactory =
        SecretsManagerFactory { kind, repository, environment -> kind.managerFor(repository, environment) },
) : ToolsEngineFactory {
    override fun create(
        tools: List<ToolAdapter>,
        workingDirectory: File,
        variables: VariableResolver,
        userHome: File,
        dryRun: Boolean,
        secretsManager: SecretsManagerKind,
    ): ToolsEngineProcessor {
        val engine = ToolsEngine(
            workingDirectory,
            variables = variables,
            userHome = userHome,
            tools = tools,
            dryRun = dryRun,
            // The manager reads the same environment as the variables of the run, so a run, and a test, decides that environment in one place.
            secretsManager = secretsManagers.create(secretsManager, workingDirectory, variables.environment),
        )
        return ToolsEngineProcessor { locations -> engine.process(locations) }
    }
}
