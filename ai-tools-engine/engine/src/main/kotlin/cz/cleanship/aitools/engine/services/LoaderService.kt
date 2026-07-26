package cz.cleanship.aitools.engine.services

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.AllManifests
import cz.cleanship.aitools.engine.models.DuplicateManifestId
import cz.cleanship.aitools.engine.models.FeatureManifest
import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.InvalidVersionException
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
        } catch (ex: InvalidVersionException) {
            // kaml lets a serializer's own failure through untouched, so a malformed 'metadata.version' would
            // otherwise reach the author as a bare "Invalid version format" naming no file at all.
            throw ManifestLoadingException(file, ex)
        }
    }

    /**
     * Loads every manifest reachable from [locations].
     *
     * Manifest ids are the primary key of the whole engine, so a collision between two files is an authoring error
     * rather than something to resolve silently. A winner is never picked behind the author's back: every manifest
     * sharing a contested id is dropped and the collision is reported, naming the id and both files.
     *
     * How far a collision reaches follows the kind of manifest, mirroring the collect-all-then-fail policy of
     * [cz.cleanship.aitools.engine.ToolsEngine]:
     * - `projects` and per-project `features` belong to a single project, so a collision there costs only the
     *   project(s) that carry it. Those projects are left out of [AllManifests.projects] and reported through
     *   [AllManifests.duplicates], which lets every unaffected project still be exported before the run fails.
     * - `agents`, `prompts`, `rulesets`, `fragments` and `skills` are shared by every project, and a project that
     *   declares no filter for a kind deploys all of it, so dropping a colliding pair would silently ship every
     *   project without content it never excluded. These therefore still fail the whole run - but only once every
     *   location has been read, so that one run reports every collision instead of one collision per run.
     *
     * @throws DuplicateManifestIdException if two distinct files of a kind shared by every project declare the
     * same id
     */
    fun loadAll(locations: Locations): AllManifests {
        val agents = loadAllFromDirectories(locations.agents, ::loadAgent)
        val prompts = loadAllFromDirectories(locations.prompts, ::loadPrompt)
        val rulesets = loadAllFromDirectories(locations.rulesets, ::loadRuleset)
        val fragments = loadAllFromDirectories(locations.fragments, ::loadFragment)
        val (skills, skillSourceDirs) = loadSkills(locations.skills)

        val projectFiles = locations.projects.flatMap { directory ->
            findYamlFiles(directory).filter { it.name == "project.yml" }
        }
        val loadedProjects = projectFiles.loadEach(::loadProject)
        val projects = loadedProjects.indexByUniqueId()
        val features = loadedProjects.associate { (projectFile, project) ->
            val featureFiles = findYamlFiles(projectFile.parentFile.resolve("features"))
            project to featureFiles.loadEach(::loadFeature).indexByUniqueId()
        }

        val scopedDuplicates = projects.duplicates + features.values.flatMap { it.duplicates }
        val sharedDuplicates = agents.duplicates + prompts.duplicates + rulesets.duplicates +
            fragments.duplicates + skills.duplicates
        if (sharedDuplicates.isNotEmpty()) {
            throw DuplicateManifestIdException(sharedDuplicates + scopedDuplicates)
        }

        // A project whose own features collide cannot be exported either: it deploys every feature it declares
        // unless it filters them, so exporting it would silently leave out the pair that had to be dropped.
        val projectsWithCollidingFeatures = features
            .filterValues { it.duplicates.isNotEmpty() }
            .keys
            .mapTo(mutableSetOf()) { it.id }

        return AllManifests(
            agents = agents.byId,
            prompts = prompts.byId,
            rulesets = rulesets.byId,
            fragments = fragments.byId,
            skills = skills.byId,
            skillSourceDirs = skillSourceDirs,
            projects = projects.byId.filterKeys { it !in projectsWithCollidingFeatures },
            features = features.mapValues { (_, featuresOfProject) -> featuresOfProject.byId },
            duplicates = scopedDuplicates,
        )
    }

    private fun loadSkills(
        directories: List<File>,
    ): Pair<IndexedManifests<SkillManifest>, Map<String, File>> {
        val loadedSkills = directories
            .filter { it.exists() }
            .mapNotNull { it.listFiles()?.toList() }
            .flatten()
            .distinctBy { it.canonicalPath }
            .mapNotNull { entry ->
                skillManifestFile(entry)?.let { LoadedSkill(entry, it, loadSkill(it)) }
            }

        val skills = loadedSkills.map { it.manifestFile to it.skill }.indexByUniqueId()
        val sourceDirs = loadedSkills
            // Only a directory-based skill has companion files to copy from its own directory, and a skill that a
            // collision kept out of the index has nothing left to copy them for.
            .filter { it.manifestFile != it.entry && it.skill.id in skills.byId }
            .associate { it.skill.id to it.entry }

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

    private fun <T : VersionedManifest> loadAllFromDirectories(
        directories: List<File>,
        loader: (File) -> T,
    ): IndexedManifests<T> = directories
        .flatMap { directory -> findYamlFiles(directory) }
        .loadEach(loader)
        .indexByUniqueId()

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
 * An index of manifests by their id, together with every collision that kept manifests out of it.
 */
private data class IndexedManifests<T : VersionedManifest>(
    val byId: Map<String, T>,
    val duplicates: List<DuplicateManifestId>,
)

/**
 * A skill manifest together with the entry it was found under: the manifest file itself for a standalone skill,
 * or the directory holding the `skill.yml` for a directory-based one.
 */
private data class LoadedSkill(
    val entry: File,
    val manifestFile: File,
    val skill: SkillManifest,
)

/**
 * Indexes manifests by their id, reporting instead of silently discarding a manifest whose id is already taken.
 * A contested id is left out of the index altogether rather than awarded to one of its claimants.
 */
private fun <T : VersionedManifest> List<Pair<File, T>>.indexByUniqueId(): IndexedManifests<T> {
    val manifests = LinkedHashMap<String, T>()
    val sourceFiles = mutableMapOf<String, File>()
    val duplicates = mutableListOf<DuplicateManifestId>()
    for ((file, manifest) in this) {
        // The first file keeps the id even once it is contested, so that a third file declaring it is reported
        // as well instead of quietly taking the place of the pair that was dropped.
        val previousFile = sourceFiles.putIfAbsent(manifest.id, file)
        if (previousFile == null) {
            manifests[manifest.id] = manifest
        } else {
            duplicates += DuplicateManifestId(manifest.id, previousFile, file)
        }
    }
    duplicates.forEach { manifests.remove(it.id) }
    return IndexedManifests(manifests, duplicates)
}

/**
 * Thrown when a manifest file cannot be turned into a model by a failure that carries no file information of its
 * own. It names the offending file, so the author is told which manifest to fix instead of only what is wrong.
 */
class ManifestLoadingException(
    val file: File,
    cause: Throwable,
) : RuntimeException("Failed to load ${file.absolutePath}: ${cause.message}", cause)

/**
 * Thrown when manifest files of a kind shared by every project declare the same id. Carries every collision found
 * in the run, so that the author is told about all of them at once instead of one per run.
 */
class DuplicateManifestIdException(
    val duplicates: List<DuplicateManifestId>,
) : RuntimeException(
        buildString {
            append("Found ${duplicates.size} duplicate manifest id(s):")
            duplicates.forEach { duplicate -> append("\n  - ${duplicate.message}") }
        },
    )
