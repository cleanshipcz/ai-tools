package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import cz.cleanship.aitools.engine.io.resolveDeclaredPath
import cz.cleanship.aitools.engine.models.ConfigManifest
import cz.cleanship.aitools.engine.models.EngineConfig
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.LocationsConfig
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException

class ConfigService {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            polymorphismStyle = PolymorphismStyle.Property,
            strictMode = false,
        ),
    )

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

        return EngineConfig(
            locations = Locations(
                agents = resolvePaths(workingDirectory, locations.agents),
                projects = resolvePaths(workingDirectory, locations.projects),
                prompts = resolvePaths(workingDirectory, locations.prompts),
                rulesets = resolvePaths(workingDirectory, locations.rulesets),
                fragments = resolvePaths(workingDirectory, locations.fragments),
                skills = resolvePaths(workingDirectory, locations.skills),
            ),
            tools = tools,
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

    private fun resolvePaths(workingDirectory: File, paths: List<String>?): List<File> =
        paths?.map(workingDirectory::resolveDeclaredPath) ?: emptyList()

    companion object {
        private val LOG = LoggerFactory.getLogger(ConfigService::class.java)
    }
}
