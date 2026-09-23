package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.io.DirectoryScan
import cz.cleanship.aitools.engine.io.PathOverlap
import cz.cleanship.aitools.engine.io.SymbolicLink
import cz.cleanship.aitools.engine.io.overlapWithRealPath
import cz.cleanship.aitools.engine.io.realPathAllowingMissing
import cz.cleanship.aitools.engine.io.scanBelow
import cz.cleanship.aitools.engine.models.AllManifests
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.serialName
import cz.cleanship.aitools.engine.services.FilterService
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.narrowedTo
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

/**
 * Refuses, before anything of the run is written, a run that would write a skill to, or delete to replace it, a path overlapping the source folder of a pointer skill, or that would delete to replace a directory holding a folder it cannot read.
 *
 * @param filterService selects the artifacts each deployment deploys, the way [ToolsEngine] selects them
 * @param tools the configured adapters of the run, whose paths are checked
 * @param userHome the home the user deployments of the run deploy under
 */
internal class SkillSourceOverlapCheck(
    private val filterService: FilterService,
    private val tools: List<ToolAdapter>,
    private val userHome: File,
) {

    /**
     * Fails when a path the run would write a skill to, or a directory it would delete to replace it, is, lies inside, or contains the source folder of a pointer skill of [allData], or holds a symbolic link leading to a path that does. Fails as well when a directory it would delete to replace it is, or holds, a folder the run cannot read.
     *
     * Every path is compared by its real location, with every symbolic link along it resolved. The paths checked are those of every skill each project and user deployment of [allData] selects, through every tool that deployment exports through, and those of every directory its `replace` deletes. Each of them is compared with the source folder of every pointer skill [allData] loaded, whether a deployment selects that skill or not. Every overlap and every unreadable folder is logged and gathered before failing. Nothing is written or deleted either way.
     *
     * @param destinations the directory each project of [allData] deploys to, by project id
     * @throws SkillSourceOverlapException if at least one such path overlaps a source folder
     * @throws UnreadableReplacedFolderException if no path overlaps a source folder, but at least one directory the run would delete to replace it is, or holds, a folder the run cannot read
     */
    fun requireApart(allData: AllManifests, destinations: Map<String, File>) {
        // Writing over a source folder would rewrite its SKILL.md, copying a companion file onto itself deletes it before it is read, and a replacing deploy removes the folder outright. The check therefore runs before any deployment is exported, in a dry run too, so the dry run preceding a deploy reports it the same way.
        val sourceFolders = allData.skills.values
            .filter { it.source != null }
            .mapNotNull { skill ->
                allData.skillSourceDirs[skill.id]?.let { SourceFolder(skill.id, allData.skillManifestFiles[skill.id], it, it.realPathAllowingMissing()) }
            }
        // The unreadable folders are found by the same walk that finds the links, so they are checked here rather than by a second walk of every replaced directory. A replaced directory is walked even when no pointer skill is loaded, because the delete would fail midway regardless.
        val targets = (projectTargets(allData, destinations) + userTargets(allData))
            .filter { sourceFolders.isNotEmpty() || it.action == DeployAction.Replace }
        val overlaps = mutableListOf<SkillSourceOverlap>()
        val unreadable = mutableListOf<UnreadableReplacedFolder>()
        for (target in targets) {
            val scan = target.path.scanBelow()
            overlaps += target.overlapsWith(sourceFolders, scan)
            if (target.action == DeployAction.Replace) {
                unreadable += scan.unreadableFolders.map { UnreadableReplacedFolder(it, target.path, target.toolType, target.deployedBy) }
            }
        }
        overlaps.forEach { LOG.error("{}", it.message) }
        unreadable.forEach { LOG.error("{}", it.message) }
        if (overlaps.isNotEmpty()) throw SkillSourceOverlapException(overlaps)
        if (unreadable.isNotEmpty()) throw UnreadableReplacedFolderException(unreadable)
    }

    private fun projectTargets(allData: AllManifests, destinations: Map<String, File>): List<DeployTarget> =
        allData.projects.values.flatMap { manifest ->
            val destination = destinations.getValue(manifest.id)
            val skillIds = filterService.filter(allData.skills.values, manifest.deploy.skills.filter).map { it.id }
            val deployedBy = "project '${manifest.id}'"
            tools.narrowedTo(manifest.deploy.tools).flatMap { adapter ->
                val skillTargets = skillIds.flatMap { skillId ->
                    adapter.skillPaths(destination, skillId).map { DeployTarget(it, DeployAction.WriteSkill(skillId), adapter.toolType, deployedBy) }
                }
                skillTargets + adapter.replacedPaths(destination, manifest).map { DeployTarget(it, DeployAction.Replace, adapter.toolType, deployedBy) }
            }
        }

    private fun userTargets(allData: AllManifests): List<DeployTarget> = allData.userDeployments.values.flatMap { manifest ->
        val skillIds = filterService.filter(allData.skills.values, manifest.skills.filter).map { it.id }
        val promptIds = filterService.filter(allData.prompts.values, manifest.prompts.filter).map { it.id }
        val agentIds = filterService.filter(allData.agents.values, manifest.agents.filter).map { it.id }
        val deployedBy = "user deployment '${manifest.id}'"
        tools.narrowedTo(manifest.tools).flatMap { adapter ->
            val exporter = adapter.userScope(userHome, manifest) ?: return@flatMap emptyList()
            val skillTargets = skillIds.flatMap { skillId ->
                exporter.skillPaths(skillId).map { DeployTarget(it, DeployAction.WriteSkill(skillId), adapter.toolType, deployedBy) }
            }
            skillTargets + exporter.replacedPaths(promptIds, agentIds, skillIds).map { DeployTarget(it, DeployAction.Replace, adapter.toolType, deployedBy) }
        }
    }

    private fun DeployTarget.overlapsWith(sourceFolders: List<SourceFolder>, scan: DirectoryScan): List<SkillSourceOverlap> {
        if (sourceFolders.isEmpty()) return emptyList()
        // Resolved once per target rather than once per source folder: a run compares a few thousand targets with a handful of folders.
        val realPath = path.realPathAllowingMissing()
        // A link below the target is where a write lands in, or what a careless delete would walk into, so it counts as leading the target there - even though the delete itself only unlinks it.
        return sourceFolders.flatMap { folder ->
            val direct = realPath.overlapWithRealPath(folder.realPath)?.let { overlapOf(folder, it, link = null) }
            val throughLinks = scan.links.mapNotNull { link -> link.destination.overlapWithRealPath(folder.realPath)?.let { overlapOf(folder, it, link) } }
            listOfNotNull(direct) + throughLinks
        }
    }

    private fun DeployTarget.overlapOf(folder: SourceFolder, overlap: PathOverlap, link: SymbolicLink?) =
        SkillSourceOverlap(folder.skillId, folder.manifestFile, folder.dir, action, path, link, overlap, toolType, deployedBy)

    /** The folder a pointer skill is read from, with its real path resolved once for the whole check. */
    private data class SourceFolder(
        val skillId: String,
        val manifestFile: File?,
        val dir: File,
        val realPath: Path,
    )

    /** One path a deployment writes to or deletes through one tool. */
    private data class DeployTarget(
        val path: File,
        val action: DeployAction,
        val toolType: ToolType,
        val deployedBy: String,
    )

    companion object {
        // Logged under the engine, which reports every other failure of the run, so one logger holds the whole transcript.
        private val LOG = LoggerFactory.getLogger(ToolsEngine::class.java)
    }
}

/**
 * What a deployment does to a path that [SkillSourceOverlapCheck] compares with the source folders of pointer skills.
 */
sealed interface DeployAction {

    /** Writes the skill [skillId] to the path. */
    data class WriteSkill(val skillId: String) : DeployAction

    /** Deletes the path together with everything under it, because the deployment sets `replace`. */
    data object Replace : DeployAction
}

/**
 * One path a deployment would write to or delete that overlaps the source folder of a pointer skill.
 *
 * @property skillId the pointer skill whose source folder the path overlaps
 * @property manifestFile the manifest of that skill, or `null` when the run did not load it from a file
 * @property sourceDir the source folder of [skillId], as its manifest resolved it
 * @property action whether the deployment writes a skill to [path] or deletes [path] to replace it
 * @property path the path the deployment writes the skill to or deletes
 * @property link the symbolic link below [path] that leads to the overlapping path, or `null` when [path] itself overlaps the source folder
 * @property overlap how [path], or the destination of [link], lies relative to the source folder
 * @property toolType the tool whose layout [path] belongs to
 * @property deployedBy the project or user deployment the path belongs to, as a message names it
 */
data class SkillSourceOverlap(
    val skillId: String,
    val manifestFile: File?,
    val sourceDir: File,
    val action: DeployAction,
    val path: File,
    val link: SymbolicLink?,
    val overlap: PathOverlap,
    val toolType: ToolType,
    val deployedBy: String,
) {
    /** Names the pointer skill, its manifest and source folder, the tool, the deployment, what it does to which path, and how that path overlaps the folder. */
    val message: String
        get() {
            val manifest = manifestFile?.let { " (${it.absolutePath})" }.orEmpty()
            val deed = when (action) {
                is DeployAction.WriteSkill -> "write the skill '${action.skillId}' for $deployedBy to '${path.absolutePath}'"
                DeployAction.Replace -> "delete '${path.absolutePath}' for $deployedBy to replace it"
            }
            val head = "Skill '$skillId'$manifest is read from the source folder '${sourceDir.absolutePath}', but ${toolType.serialName} would $deed"
            // The delete of a replacing deploy only unlinks a link below the replaced directory, so for that case the message says where the link leads instead of claiming the source would be damaged.
            if (action == DeployAction.Replace && link != null) {
                return "$head, and the link '${link.path.absolutePath}' below the directory to be replaced leads to '${link.destination}', which ${overlap.description} that folder. " +
                    "The deploy would only unlink it, but it refuses while a link below a replaced directory leads into a source folder. " +
                    "Remove the link '${link.path.absolutePath}', or turn off 'replace' for that deployment."
            }
            val how = when (link) {
                null -> "which ${overlap.description} that folder once links are resolved"
                else -> "and the link '${link.path.absolutePath}' in it leads to '${link.destination}', which ${overlap.description} that folder"
            }
            val advice = when (action) {
                is DeployAction.WriteSkill -> "Remove the link or folder that leads there, or deselect the skill '${action.skillId}' for that deployment."
                DeployAction.Replace -> "Move the source folder, or remove the link to it, so that it neither is, contains, nor lies inside '${path.absolutePath}', or turn off 'replace' for that deployment."
            }
            return "$head, $how. Deploying would overwrite or delete the files of the source. $advice"
        }
}

/**
 * Thrown before anything of the run is written, when at least one path the run would write a skill to or delete overlaps the source folder of a pointer skill - see [SkillSourceOverlapCheck.requireApart].
 *
 * @property overlaps every overlap the run found
 */
class SkillSourceOverlapException(
    val overlaps: List<SkillSourceOverlap>,
) : RuntimeException(
        buildString {
            append("Refusing to deploy: ${overlaps.size} path(s) the run would write or delete overlap the source folder of a pointer skill; nothing was written:")
            overlaps.forEach { overlap -> append("\n  - ${overlap.message}") }
        },
    )

/**
 * One folder a deployment cannot read inside a directory it would delete to replace it.
 *
 * @property folder the folder that cannot be read, named under [path]
 * @property path the directory the deployment deletes to replace it
 * @property toolType the tool whose layout [path] belongs to
 * @property deployedBy the project or user deployment the path belongs to, as a message names it
 */
data class UnreadableReplacedFolder(
    val folder: File,
    val path: File,
    val toolType: ToolType,
    val deployedBy: String,
) {
    /** Names the tool, the deployment, the replaced directory and the folder, and what to do about it. */
    val message: String
        get() = "${toolType.serialName} would delete '${path.absolutePath}' for $deployedBy to replace it, but cannot read '${folder.absolutePath}', so the delete would stop midway. " +
            "Make that folder readable and writable, remove it, or turn off 'replace' for that deployment."
}

/**
 * Thrown before anything of the run is written, when at least one directory the run would delete to replace it is, or holds, a folder the run cannot read - see [SkillSourceOverlapCheck.requireApart].
 *
 * @property folders every unreadable folder the run found
 */
class UnreadableReplacedFolderException(
    val folders: List<UnreadableReplacedFolder>,
) : RuntimeException(
        buildString {
            append("Refusing to deploy: ${folders.size} folder(s) inside a directory the run would delete to replace it cannot be read; nothing was written:")
            folders.forEach { folder -> append("\n  - ${folder.message}") }
        },
    )
