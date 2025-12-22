package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import cz.cleanship.aitools.engine.models.*
import kotlinx.serialization.decodeFromString
import java.io.File

class LoaderService {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
//            strictMode = false, // Do not allow unknown keys
        ),
    )

    fun loadAgent(file: File): AgentManifest = yaml.load(file)

    fun loadPrompt(file: File): PromptManifest = yaml.load(file)

    fun loadRulepack(file: File): RulepackManifest = yaml.load(file)

    fun loadFeature(file: File): FeatureManifest = yaml.load(file)

    inline fun <reified T> Yaml.load(file: File): T {
        try {
            val content = file.readText()
            return decodeFromString(content)
        } catch (ex: YamlException) {
            throw YamlException("Failed to load ${file.absolutePath}", ex.path, ex)
        }
    }

    fun loadAll(locations: Locations): AllManifests = AllManifests(
        agents = loadAllFromDirectories(locations.agents, ::loadAgent),
        features = loadAllFromDirectories(locations.features, ::loadFeature),
        prompts = loadAllFromDirectories(locations.prompts, ::loadPrompt),
        rulepacks = loadAllFromDirectories(locations.rulepacks, ::loadRulepack),
    )

    private fun <T : VersionedManifest> loadAllFromDirectories(directories: List<File>, loader: (File) -> T): Map<String, T> =
        directories.flatMap { directory ->
            findYamlFiles(directory).map { loader(it) }
        }.associateBy { it.id }

    fun findYamlFiles(directory: File): List<File> = directory
        .walkTopDown()
        .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        .toList()
}

data class Locations(
    val agents: List<File>,
    val features: List<File>,
    val prompts: List<File>,
    val rulepacks: List<File>,
)
