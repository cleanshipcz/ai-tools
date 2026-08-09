package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.io.resolveDeclaredPath
import cz.cleanship.aitools.engine.models.ConfigManifest
import cz.cleanship.aitools.engine.models.EngineConfig
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.LocationsConfig
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

/**
 * @param environment where a `${NAME}` reference that the `env_vars` of the config files do not declare is looked up
 */
class ConfigService(
    private val environment: EnvironmentSource = EnvironmentSource.PROCESS,
) {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            polymorphismStyle = PolymorphismStyle.Property,
            strictMode = false,
        ),
    )

    /**
     * Reads `config.yml` and the `config.local.yml` beside it into the configuration of one run.
     *
     * Every `locations.*` entry is substituted with the variables of the run before it is resolved into a directory,
     * so a variable may expand to the absolute base a relative path would otherwise be denied - see [VariableResolver].
     * The resolver itself travels on in [EngineConfig.variables], because `deploy.directory` of a project manifest is
     * substituted with the same variables, and that manifest is only read once these locations are known.
     *
     * @throws FileNotFoundException if the working directory holds no `config.yml`
     * @throws cz.cleanship.aitools.engine.env.VariableSubstitutionException if a location references a variable that
     * neither the config files nor the environment declare
     */
    fun loadConfig(workingDirectory: File = File(".")): EngineConfig {
        // TODO make config file location configurable
        // config.yml and config.local.yml are defaults that can be overriden based on the strategy
        // default strategy, key=merge -> config, then config.local, then provided (just like config vs config.local)
        // key=override -> provided config is the only relevant (this is when e.g. I want to deploy only a specific subset of projects/tools)
        val defaultConfig = loadConfigFile(File(workingDirectory, "config.yml"))
            ?: throw FileNotFoundException("Missing default config file: ${File(workingDirectory, "config.yml").absolutePath}")
        val localConfig = loadConfigFile(File(workingDirectory, "config.local.yml")) ?: ConfigManifest()

        val mergedConfig = merge(defaultConfig, localConfig)
        val locations = mergedConfig.locations ?: LocationsConfig()
        val tools = mergedConfig.tools ?: emptyList()
        val variables = VariableResolver(mergedConfig.envVars.orEmpty(), environment)

        return EngineConfig(
            locations = Locations(
                agents = resolvePaths(workingDirectory, locations.agents, variables, "agents"),
                projects = resolvePaths(workingDirectory, locations.projects, variables, "projects"),
                prompts = resolvePaths(workingDirectory, locations.prompts, variables, "prompts"),
                rulesets = resolvePaths(workingDirectory, locations.rulesets, variables, "rulesets"),
                fragments = resolvePaths(workingDirectory, locations.fragments, variables, "fragments"),
                skills = resolvePaths(workingDirectory, locations.skills, variables, "skills"),
            ),
            tools = tools,
            variables = variables,
        )
    }

    private fun loadConfigFile(file: File): ConfigManifest? = if (file.exists()) {
        LOG.info("Loading config from {}", file.absolutePath)
        yaml.decodeFromString<ConfigManifest>(file.readText())
    } else {
        LOG.debug("Config file not found: {}", file.absolutePath)
        null
    }

    private fun merge(default: ConfigManifest, local: ConfigManifest?): ConfigManifest {
        if (local == null) return default

        return ConfigManifest(
            locations = mergeLocations(default.locations, local.locations),
            tools = local.tools ?: default.tools,
            // Variables merge per key rather than as a whole: a local config usually redeclares the one base that
            // differs on its machine, and replacing the whole map would silently drop the ones it agrees with.
            envVars = default.envVars.orEmpty() + local.envVars.orEmpty(),
        )
    }

    private fun mergeLocations(default: LocationsConfig?, local: LocationsConfig?): LocationsConfig? {
        if (default == null) return local
        if (local == null) return default

        return LocationsConfig(
            agents = local.agents ?: default.agents,
            projects = local.projects ?: default.projects,
            prompts = local.prompts ?: default.prompts,
            rulesets = local.rulesets ?: default.rulesets,
            fragments = local.fragments ?: default.fragments,
            skills = local.skills ?: default.skills,
        )
    }

    /**
     * @param field the key of `locations` these [paths] were declared under, named in a substitution failure so the
     * author is told which list to fix
     */
    private fun resolvePaths(
        workingDirectory: File,
        paths: List<String>?,
        variables: VariableResolver,
        field: String,
    ): List<File> = paths?.map { path -> workingDirectory.resolveDeclaredPath(variables.substitute(path, origin = "locations.$field of config.yml or config.local.yml")) } ?: emptyList()

    companion object {
        private val LOG = LoggerFactory.getLogger(ConfigService::class.java)
    }
}
