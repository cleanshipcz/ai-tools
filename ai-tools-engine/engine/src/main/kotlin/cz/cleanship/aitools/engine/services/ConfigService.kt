package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import cz.cleanship.aitools.engine.models.LocationsConfig
import cz.cleanship.aitools.engine.models.EngineConfig
import kotlinx.serialization.decodeFromString
import org.slf4j.LoggerFactory
import java.io.File

class ConfigService {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            polymorphismStyle = PolymorphismStyle.Property,
            strictMode = false,
        ),
    )

    fun loadConfig(workingDirectory: File = File(".")): Locations {
        val defaultConfig = loadConfigFile(File(workingDirectory, "config.yml")) ?: EngineConfig()
        val localConfig = loadConfigFile(File(workingDirectory, "config.local.yml"))

        val mergedConfig = merge(defaultConfig, localConfig)
        val locations = mergedConfig.locations ?: LocationsConfig()

        return Locations(
            agents = resolvePaths(workingDirectory, locations.agents),
            projects = resolvePaths(workingDirectory, locations.projects),
            prompts = resolvePaths(workingDirectory, locations.prompts),
            rulesets = resolvePaths(workingDirectory, locations.rulesets),
        )
    }

    private fun loadConfigFile(file: File): EngineConfig? {
        return if (file.exists()) {
            LOG.info("Loading config from {}", file.absolutePath)
            try {
                yaml.decodeFromString<EngineConfig>(file.readText())
            } catch (e: Exception) {
                LOG.error("Failed to parse config file: {}", file.absolutePath, e)
                null
            }
        } else {
            LOG.debug("Config file not found: {}", file.absolutePath)
            null
        }
    }

    private fun merge(default: EngineConfig, local: EngineConfig?): EngineConfig {
        if (local == null) return default

        return EngineConfig(
            locations = mergeLocations(default.locations, local.locations)
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
        )
    }

    private fun resolvePaths(workingDirectory: File, paths: List<String>?): List<File> {
        return paths?.map { path ->
            val file = File(path)
            if (file.isAbsolute) {
                file
            } else {
                File(workingDirectory, path).absoluteFile
            }
        } ?: emptyList()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ConfigService::class.java)
    }
}
