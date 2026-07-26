package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.AllManifests
import cz.cleanship.aitools.engine.models.FeatureManifest
import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.SkillManifest
import cz.cleanship.aitools.engine.models.VersionedManifest
import kotlinx.serialization.decodeFromString
import java.io.File

class LoaderService {
    private val yaml = Yaml(
        configuration = YamlConfiguration(
            polymorphismStyle = PolymorphismStyle.Property,
        ),
    )

    fun loadAgent(file: File): AgentManifest = yaml.load(file)

    fun loadPrompt(file: File): PromptManifest = yaml.load(file)

    fun loadRuleset(file: File): RulesetManifest = yaml.load(file)

    fun loadFragment(file: File): FragmentManifest = yaml.load(file)

    fun loadFeature(file: File): FeatureManifest = yaml.load(file)

    fun loadSkill(file: File): SkillManifest = yaml.load(file)

    fun loadProject(file: File): ProjectManifest = yaml.load(file)

    inline fun <reified T> Yaml.load(file: File): T {
        try {
            val content = file.readText()
            return decodeFromString(content)
        } catch (ex: YamlException) {
            throw YamlException("Failed to load ${file.absolutePath}", ex.path, ex)
        }
    }

    /**
     * Loads every manifest reachable from [locations].
     *
     * Manifest ids are the primary key of the whole engine, so a collision between two files is an authoring error
     * rather than something to resolve silently: it fails with [DuplicateManifestIdException] naming both files.
     *
     * @throws DuplicateManifestIdException if two distinct files of the same kind declare the same id
     */
    fun loadAll(locations: Locations): AllManifests {
        val projectFiles = locations.projects.flatMap { directory ->
            findYamlFiles(directory).filter { it.name == "project.yml" }
        }
        val loadedProjects = projectFiles.loadEach(::loadProject)
        val projectsWithFeatures = loadedProjects.map { (projectFile, project) ->
            val featureFiles = findYamlFiles(projectFile.parentFile.resolve("features"))
            project to featureFiles.loadEach(::loadFeature).associateByUniqueId()
        }
        val loadedSkills = loadSkills(locations.skills)
        return AllManifests(
            agents = loadAllFromDirectories(locations.agents, ::loadAgent),
            prompts = loadAllFromDirectories(locations.prompts, ::loadPrompt),
            rulesets = loadAllFromDirectories(locations.rulesets, ::loadRuleset),
            fragments = loadAllFromDirectories(locations.fragments, ::loadFragment),
            skills = loadedSkills.first,
            skillSourceDirs = loadedSkills.second,
            projects = loadedProjects.associateByUniqueId(),
            features = projectsWithFeatures.toMap(),
        )
    }

    private fun loadSkills(
        directories: List<File>,
    ): Pair<Map<String, SkillManifest>, Map<String, File>> {
        val skills = LinkedHashMap<String, SkillManifest>()
        val sourceDirs = mutableMapOf<String, File>()
        val sourceFiles = mutableMapOf<String, File>()

        directories
            .filter { it.exists() }
            .mapNotNull { it.listFiles()?.toList() }
            .flatten()
            .distinctBy { it.canonicalPath }
            .forEach { entry ->
                val manifestFile = skillManifestFile(entry)
                if (manifestFile != null) {
                    val skill = loadSkill(manifestFile)
                    val previousFile = sourceFiles.put(skill.id, manifestFile)
                    if (previousFile != null) {
                        throw DuplicateManifestIdException(skill.id, previousFile, manifestFile)
                    }
                    skills[skill.id] = skill
                    // Only a directory-based skill has companion files to copy from its own directory.
                    if (manifestFile != entry) {
                        sourceDirs[skill.id] = entry
                    }
                }
            }

        return skills to sourceDirs
    }

    /**
     * Returns the manifest file describing the skill at [entry], or `null` when [entry] is not a skill.
     * A skill is either a standalone YAML file or a directory containing a `skill.yml`.
     */
    private fun skillManifestFile(entry: File): File? = when {
        entry.isFile && (entry.extension == "yml" || entry.extension == "yaml") -> entry
        entry.isDirectory && File(entry, "skill.yml").exists() -> File(entry, "skill.yml")
        else -> null
    }

    private fun <T : VersionedManifest> loadAllFromDirectories(directories: List<File>, loader: (File) -> T): Map<String, T> =
        directories
            .flatMap { directory -> findYamlFiles(directory) }
            .loadEach(loader)
            .associateByUniqueId()

    fun findYamlFiles(directory: File): List<File> = directory
        .walkTopDown()
        .filter { it.isFile && (it.extension == "yml" || it.extension == "yaml") }
        .toList()
}

/**
 * Loads every file with [loader], keeping each loaded manifest paired with the file it came from so that a later
 * id collision can name both sources. Files reachable through several overlapping [Locations] entries are loaded
 * once, so overlapping configuration never looks like a duplicate id.
 */
private fun <T : VersionedManifest> List<File>.loadEach(loader: (File) -> T): List<Pair<File, T>> =
    distinctBy { it.canonicalPath }.map { file -> file to loader(file) }

/**
 * Indexes manifests by their id, failing instead of silently discarding a manifest whose id is already taken.
 */
private fun <T : VersionedManifest> List<Pair<File, T>>.associateByUniqueId(): Map<String, T> {
    val manifests = LinkedHashMap<String, T>()
    val sourceFiles = mutableMapOf<String, File>()
    for ((file, manifest) in this) {
        val previousFile = sourceFiles.put(manifest.id, file)
        if (previousFile != null) {
            throw DuplicateManifestIdException(manifest.id, previousFile, file)
        }
        manifests[manifest.id] = manifest
    }
    return manifests
}

/**
 * Thrown when two distinct manifest files of the same kind declare the same id.
 */
class DuplicateManifestIdException(
    val id: String,
    val firstFile: File,
    val secondFile: File,
) : RuntimeException(
        "Duplicate manifest id '$id' declared in both ${firstFile.absolutePath} and ${secondFile.absolutePath}. " +
            "Manifest ids must be unique - rename one of them.",
    )
