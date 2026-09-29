package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlNode
import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.io.resolveDeclaredPath
import cz.cleanship.aitools.engine.models.ConfigManifest
import cz.cleanship.aitools.engine.models.EngineConfig
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.LocationsConfig
import cz.cleanship.aitools.engine.models.SecretsManagerKind
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.encoding.CompositeDecoder
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
     * A key of either file that this engine does not know is ignored, and logged as a warning naming the file and the key.
     *
     * @throws FileNotFoundException if the working directory holds no `config.yml`
     * @throws cz.cleanship.aitools.engine.env.VariableSubstitutionException if a location references a variable that
     * neither the config files nor the environment declare
     * @throws RetiredConfigKeyException if a config file declares a key this engine has retired - see [rejectRetiredKeys]
     * @throws InvalidConfigValueException if `secrets_manager` of the file that decides it names a secrets manager this engine does not know
     */
    fun loadConfig(workingDirectory: File = File(".")): EngineConfig {
        // TODO make config file location configurable
        // config.yml and config.local.yml are defaults that can be overriden based on the strategy
        // default strategy, key=merge -> config, then config.local, then provided (just like config vs config.local)
        // key=override -> provided config is the only relevant (this is when e.g. I want to deploy only a specific subset of projects/tools)
        val defaultConfig = loadConfigFile(File(workingDirectory, DEFAULT_CONFIG_FILE))
            ?: throw FileNotFoundException("Missing default config file: ${File(workingDirectory, DEFAULT_CONFIG_FILE).absolutePath}")
        val localConfig = loadConfigFile(File(workingDirectory, LOCAL_CONFIG_FILE)) ?: ConfigManifest()

        rejectRetiredKeys(defaultConfig, localConfig)

        val tools = localConfig.tools ?: defaultConfig.tools ?: emptyList()
        // Variables merge per key rather than as a whole: a local config usually redeclares the one base that differs
        // on its machine, and replacing the whole map would silently drop the ones it agrees with.
        val variables = VariableResolver(defaultConfig.envVars.orEmpty() + localConfig.envVars.orEmpty(), environment)

        return EngineConfig(
            locations = resolveLocations(workingDirectory, defaultConfig.locations, localConfig.locations, variables),
            tools = tools,
            variables = variables,
            secretsManager = secretsManagerOf(defaultConfig, localConfig),
        )
    }

    /**
     * Returns the secrets manager `config.local.yml` names, otherwise the one `config.yml` names, otherwise [SecretsManagerKind.DEFAULT].
     *
     * @throws InvalidConfigValueException naming the file and the accepted values if the file that decides names a secrets manager this engine does not know
     */
    private fun secretsManagerOf(defaultConfig: ConfigManifest, localConfig: ConfigManifest): SecretsManagerKind {
        val (declared, declaredIn) = localConfig.secretsManager?.let { it to LOCAL_CONFIG_FILE }
            ?: defaultConfig.secretsManager?.let { it to DEFAULT_CONFIG_FILE }
            ?: return SecretsManagerKind.DEFAULT
        // The value is not repeated: it is the one thing in the message that did not come from the engine.
        return SecretsManagerKind.ofConfigValue(declared) ?: throw InvalidConfigValueException(
            "'secrets_manager' of $declaredIn names a secrets manager this engine does not know. " +
                "Accepted values: ${SecretsManagerKind.entries.joinToString { "'${it.configValue}'" }}.",
        )
    }

    /**
     * Fails when a config file still declares `locations.projects`, which was renamed to `locations.deployments`
     * once the directories began holding two kinds of deployment manifest.
     *
     * Unknown keys are otherwise tolerated here, with a warning - [YamlConfiguration.strictMode] is off, so that a config written
     * for a newer engine still loads on an older one. That tolerance is exactly what would make this rename lossy:
     * the retired list would be dropped without a word, `deployments` would resolve to nothing, and the run would
     * deploy nothing while exiting successfully. Naming the rename costs one release of an explicit failure and
     * saves a silent one.
     *
     * @throws RetiredConfigKeyException naming the retired key, its replacement and the file that declared it
     */
    private fun rejectRetiredKeys(defaultConfig: ConfigManifest, localConfig: ConfigManifest) {
        val declaredIn = when {
            localConfig.locations?.projects != null -> LOCAL_CONFIG_FILE
            defaultConfig.locations?.projects != null -> DEFAULT_CONFIG_FILE
            else -> return
        }
        throw RetiredConfigKeyException(
            "'locations.projects' of $declaredIn was renamed to 'locations.deployments', because those directories " +
                "now hold both project.yml and user.yml manifests. Rename the key to keep deploying them.",
        )
    }

    private fun loadConfigFile(file: File): ConfigManifest? = if (file.exists()) {
        LOG.info("Loading config from {}", file.absolutePath)
        val text = file.readText()
        yaml.decodeFromString<ConfigManifest>(text).also { warnUnknownKeys(file.name, yaml.parseToYamlNode(text), ConfigManifest.serializer().descriptor) }
    } else {
        LOG.debug("Config file not found: {}", file.absolutePath)
        null
    }

    /**
     * Logs a warning naming [fileName] and the key for every key of [node] that [descriptor] does not declare, at any depth of a nested setting; the free-form maps and lists below a key, such as `env_vars`, are not looked into.
     */
    // Decoding is lenient, so that a config written for a newer engine still loads; the warning keeps a misspelled key, such as a secrets_manager that would leave the default in force, from being dropped without a word.
    private fun warnUnknownKeys(fileName: String, node: YamlNode, descriptor: SerialDescriptor, prefix: String = "") {
        val map = node as? YamlMap ?: return
        map.entries.forEach { (key, value) ->
            val index = descriptor.getElementIndex(key.content)
            if (index == CompositeDecoder.UNKNOWN_NAME) {
                LOG.warn(
                    "{} declares the key '{}', which this engine does not know, so it is ignored. Check its spelling; a key of a newer engine is ignored the same way.",
                    fileName,
                    "$prefix${key.content}",
                )
            } else {
                val element = descriptor.getElementDescriptor(index)
                if (element.kind == StructureKind.CLASS) warnUnknownKeys(fileName, value, element, "$prefix${key.content}.")
            }
        }
    }

    /**
     * Resolves every `locations.*` list into the directories the run reads manifests from, substituting the variables
     * of the run into each entry first.
     *
     * A list is taken whole from the one file that declares it - `config.local.yml` when it declares one, `config.yml`
     * otherwise - rather than being merged entry by entry. That is what lets a substitution failure name the single
     * file the offending value was written in, instead of both candidates.
     */
    private fun resolveLocations(
        workingDirectory: File,
        default: LocationsConfig?,
        local: LocationsConfig?,
        variables: VariableResolver,
    ): Locations {
        fun resolve(field: String, select: (LocationsConfig) -> List<String>?): List<File> {
            val declaredLocally = local?.let(select)
            val declared = declaredLocally ?: default?.let(select) ?: return emptyList()
            val declaredIn = if (declaredLocally != null) LOCAL_CONFIG_FILE else DEFAULT_CONFIG_FILE
            return declared.map { path -> workingDirectory.resolveDeclaredPath(variables.substitute(path, origin = "locations.$field of $declaredIn")) }
        }

        return Locations(
            agents = resolve("agents") { it.agents },
            deployments = resolve("deployments") { it.deployments },
            prompts = resolve("prompts") { it.prompts },
            rulesets = resolve("rulesets") { it.rulesets },
            fragments = resolve("fragments") { it.fragments },
            skills = resolve("skills") { it.skills },
            mcps = resolve("mcps") { it.mcps },
        )
    }

    companion object {
        private const val DEFAULT_CONFIG_FILE = "config.yml"
        private const val LOCAL_CONFIG_FILE = "config.local.yml"
        private val LOG = LoggerFactory.getLogger(ConfigService::class.java)
    }
}

/**
 * Thrown when `config.yml` or `config.local.yml` declares something this engine cannot run with, naming the file and what to write instead.
 */
sealed class ConfigFileException(message: String) : RuntimeException(message)

/**
 * Thrown when a config file declares a key this engine has retired, naming what to write instead - see
 * [ConfigService.rejectRetiredKeys].
 */
class RetiredConfigKeyException(message: String) : ConfigFileException(message)

/**
 * Thrown when a config file declares a value this engine does not accept for a key, naming the key, the file and the accepted values, never the value.
 */
class InvalidConfigValueException(message: String) : ConfigFileException(message)
