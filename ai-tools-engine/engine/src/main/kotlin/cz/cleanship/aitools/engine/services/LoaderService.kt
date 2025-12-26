package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.AllManifests
import cz.cleanship.aitools.engine.models.FeatureManifest
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.VersionedManifest
import kotlinx.serialization.decodeFromString
import java.io.File

class LoaderService {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            polymorphismStyle = PolymorphismStyle.Property,
//            strictMode = false, // Do not allow unknown keys
        ),
    )

    fun loadAgent(file: File): AgentManifest = yaml.load(file)

    fun loadPrompt(file: File): PromptManifest = yaml.load(file)

    fun loadRuleset(file: File): RulesetManifest = yaml.load(file)

    fun loadFeature(file: File): FeatureManifest = yaml.load(file)

    fun loadProject(file: File): ProjectManifest = yaml.load(file)

    inline fun <reified T> Yaml.load(file: File): T {
        try {
            val content = file.readText()
            return decodeFromString(content)
        } catch (ex: YamlException) {
            throw YamlException("Failed to load ${file.absolutePath}", ex.path, ex)
        }
    }

    fun loadAll(locations: Locations): AllManifests {
        val projectFiles = locations.projects.flatMap { directory ->
            findYamlFiles(directory).filter { it.name == "project.yml" }
        }
        val projectsWithFeatures = projectFiles.map { projectFile ->
            val project = loadProject(projectFile)
            val features = findYamlFiles(projectFile.parentFile.resolve("features")).map { featureFile ->
                loadFeature(featureFile)
            }
            project to features
        }
        return AllManifests(
            agents = loadAllFromDirectories(locations.agents, ::loadAgent),
            prompts = loadAllFromDirectories(locations.prompts, ::loadPrompt),
            rulesets = loadAllFromDirectories(locations.rulesets, ::loadRuleset),
            projects = projectsWithFeatures.map { it.first }.associateBy { it.id },
            features = projectsWithFeatures.associate { it.first to it.second.associateBy { f -> f.id } }
        )
    }

    private fun <T : VersionedManifest> loadAllFromDirectories(directories: List<File>, loader: (File) -> T, filter: (File) -> Boolean = { true }): Map<String, T> =
        directories.flatMap { directory ->
            findYamlFiles(directory).filter(filter).map { loader(it) }
        }.associateBy { it.id }

    fun findYamlFiles(directory: File): List<File> = directory
        .walkTopDown()
        .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        .toList()
}

data class Locations(
    val agents: List<File>,
    val projects: List<File>,
    val prompts: List<File>,
    val rulesets: List<File>,
)
