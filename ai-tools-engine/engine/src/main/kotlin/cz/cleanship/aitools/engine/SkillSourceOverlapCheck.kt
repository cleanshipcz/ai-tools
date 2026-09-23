package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.io.DirectoryScan
import cz.cleanship.aitools.engine.io.PathOverlap
import cz.cleanship.aitools.engine.io.SymbolicLink
import cz.cleanship.aitools.engine.io.checkArtifactDirectoryWithin
import cz.cleanship.aitools.engine.io.linkDestination
import cz.cleanship.aitools.engine.io.overlapWithRealPath
import cz.cleanship.aitools.engine.io.realPathAllowingMissing
import cz.cleanship.aitools.engine.io.scanBelow
import cz.cleanship.aitools.engine.models.AllManifests
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.serialName
import cz.cleanship.aitools.engine.services.FilterService
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.UserScopeExporter
import cz.cleanship.aitools.engine.tools.narrowedTo
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Refuses, before anything of the run is written, a run that would write a skill to, or delete to replace it, a path overlapping the source folder of a pointer skill, that would delete to replace a directory of a user deployment outside the directory it owns, or that would delete to replace a directory holding a folder it cannot read.
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
     * Fails when a path the run would write a skill to, or a directory it would delete to replace it, is, lies inside, or contains the source folder of a pointer skill of [allData], or holds a symbolic link leading to a path that does. Fails as well when a directory a user deployment would delete to replace it does not lie inside the directory it owns, or when a directory the run would delete to replace it is, or holds, a folder the run cannot read, unless that directory is, or lies below, a replaced symbolic link of the same deployment and tool, whose destination the delete never opens.
     *
     * Every path is compared by its real location at the time the run writes or deletes it, with every symbolic link along it resolved. A replaced directory that is itself a symbolic link is compared by where it sits and by where it leads, but not by the links in the folder it leads to. A replaced directory that is not a symbolic link is compared by where it lies and by every link below it. Every other path at or below a replaced directory of the same deployment and tool is compared by where it lies once that directory is deleted. The paths checked are those of every skill each project and user deployment of [allData] selects, through every tool that deployment exports through, and those of every directory its `replace` deletes. Each of them is compared with the source folder of every pointer skill [allData] loaded, whether a deployment selects that skill or not. Every overlap and every unreadable folder is logged and gathered before failing. Nothing is written or deleted either way.
     *
     * @param destinations the directory each project of [allData] deploys to, by project id
     * @throws SkillSourceOverlapException if at least one such path overlaps a source folder
     * @throws cz.cleanship.aitools.engine.io.ArtifactPathException if no path overlaps a source folder, but a directory a user deployment would delete to replace it does not lie inside the directory it owns, judged by where it leads when it is a symbolic link - see [cz.cleanship.aitools.engine.io.checkArtifactDirectoryWithin]
     * @throws UnreadableReplacedFolderException if no path overlaps a source folder and every replaced directory of a user deployment lies inside the directory it owns, but a directory the run would delete to replace it is, or holds, a folder the run cannot read; a directory that is, or lies below, a replaced symbolic link of the same deployment and tool is not checked
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
        // The delete removes a replaced directory together with every link below it, and a replaced directory that is itself a link as one entry, without opening what any of them leads to, or in the user scope refuses one that leads outside the skills folder before writing through it, which the containment check below does before any write of the run. The same deployment writes through that tool only after the delete, so every other path at or below a replaced directory lands where it lies once that directory is gone.
        val replaced = targets.filter { it.action == DeployAction.Replace }
        val overlaps = mutableListOf<SkillSourceOverlap>()
        val unreadable = mutableListOf<UnreadableReplacedFolder>()
        for (target in targets) {
            // The outermost one, because deleting it removes everything below it, a replaced directory nested in it included; every candidate holds the target, so the shortest path is the outermost.
            val deletedWith = replaced
                .filter { it.isOrHolds(target) }
                .minByOrNull { it.path.absolutePath.length }
            val linked = deletedWith != null && Files.isSymbolicLink(deletedWith.path.toPath())
            // A replaced directory that is not a link is where it lies when deleted; every other path at or below a replaced directory is compared as it will be once that directory is gone.
            val removedFirst = deletedWith?.takeUnless { it == target && !linked }
            // Nothing is left below a removed link, and a path held by a replaced directory other than itself is covered by the walk of that directory, so neither is walked here.
            val scan = if (removedFirst == null) target.path.scanBelow() else DirectoryScan(emptyList(), emptyList())
            overlaps += target.overlapsWith(sourceFolders, scan, removedFirst, linked)
            if (target.action == DeployAction.Replace) {
                unreadable += scan.unreadableFolders.map { UnreadableReplacedFolder(it, target.path, target.toolType, target.deployedBy) }
            }
        }
        overlaps.forEach { LOG.error("{}", it.message) }
        if (overlaps.isNotEmpty()) throw SkillSourceOverlapException(overlaps)
        // The export applies the same check to each of these directories only when it reaches it, after the projects of the run and the instructions file of the home are written, so it is applied to all of them here first.
        targets.forEach { target -> target.containment?.let { target.path.checkArtifactDirectoryWithin(it.owned, it.describedBy) } }
        unreadable.forEach { LOG.error("{}", it.message) }
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
            skillTargets + exporter.describedReplacedPaths(promptIds, agentIds, skillIds).map { (path, describedBy) ->
                DeployTarget(path, DeployAction.Replace, adapter.toolType, deployedBy, Containment(exporter.replacedWithin, describedBy))
            }
        }
    }

    /**
     * Returns every path of [UserScopeExporter.replacedPaths] for [promptIds], [agentIds] and [skillIds], each with the artifact it is replaced for, named the way the export names it when it refuses that path.
     */
    // replacedPaths does not say whose each path is, so it is asked once per artifact.
    private fun UserScopeExporter.describedReplacedPaths(
        promptIds: Collection<String>,
        agentIds: Collection<String>,
        skillIds: Collection<String>,
    ): List<Pair<File, String>> =
        promptIds.flatMap { id -> replacedPaths(listOf(id), emptyList(), emptyList()).map { it to "prompt '$id'" } } +
            agentIds.flatMap { id -> replacedPaths(emptyList(), listOf(id), emptyList()).map { it to "agent '$id'" } } +
            skillIds.flatMap { id -> replacedPaths(emptyList(), emptyList(), listOf(id)).map { it to "skill '$id'" } }

    /**
     * Returns every overlap of this target with [sourceFolders] and with the destinations of the links of [scan], comparing the target where it lies once [removedFirst], the replaced directory at or above it, is deleted, and otherwise where it lies now; [linked] tells whether [removedFirst] is a symbolic link.
     */
    private fun DeployTarget.overlapsWith(
        sourceFolders: List<SourceFolder>,
        scan: DirectoryScan,
        removedFirst: DeployTarget?,
        linked: Boolean,
    ): List<SkillSourceOverlap> {
        if (sourceFolders.isEmpty()) return emptyList()
        // Resolved once per target rather than once per source folder: a run compares a few thousand targets with a handful of folders.
        val realPath = if (removedFirst == null) path.realPathAllowingMissing() else removedFirst.path.realPathOnceDeleted(path)
        // Where a replaced link leads is reported through that link, like a link below a replaced directory, because the delete removes the link and not what it leads to; where it sits is compared directly, because removing it changes that folder.
        val rootLink = if (removedFirst == this && linked) SymbolicLink(path, path.absoluteFile.toPath().linkDestination()) else null
        // Named in the message, because only removing that link places the path where it overlaps, which is why removing the link or deselecting the skill would not help.
        val unlinked = removedFirst?.path?.takeIf { linked }
        // A link below the target is where a write lands in, or what a careless delete would walk into, so it counts as leading the target there - even though the delete itself only unlinks it.
        return sourceFolders.flatMap { folder ->
            val direct = realPath.overlapWithRealPath(folder.realPath)?.let { overlapOf(folder, it, null, unlinked) }
            val throughLinks = (listOfNotNull(rootLink) + scan.links).mapNotNull { link ->
                link.destination.overlapWithRealPath(folder.realPath)?.let { overlapOf(folder, it, link, null) }
            }
            listOfNotNull(direct) + throughLinks
        }
    }

    /**
     * Returns whether [other] is this target or lies below it within the same deployment and tool, whose writes follow the delete of this target.
     */
    private fun DeployTarget.isOrHolds(other: DeployTarget): Boolean {
        val held = other.path.absoluteFile.toPath()
        return deployedBy == other.deployedBy && toolType == other.toolType && held.startsWith(path.absoluteFile.toPath())
    }

    /**
     * Returns the real path of [atOrBelow], which is this replaced directory or a path below it, once this directory is deleted: where this directory sits, with every link along its parent resolved, followed by the rest of [atOrBelow].
     */
    private fun File.realPathOnceDeleted(atOrBelow: File): Path {
        val link = absoluteFile.toPath()
        val location = link.parent
            .toFile()
            .realPathAllowingMissing()
            .resolve(link.fileName)
        return location.resolve(link.relativize(atOrBelow.absoluteFile.toPath())).normalize()
    }

    private fun DeployTarget.overlapOf(folder: SourceFolder, overlap: PathOverlap, link: SymbolicLink?, unlinked: File?) =
        SkillSourceOverlap(folder.skillId, folder.manifestFile, folder.dir, action, path, link, overlap, toolType, deployedBy, unlinked)

    /** The folder a pointer skill is read from, with its real path resolved once for the whole check. */
    private data class SourceFolder(
        val skillId: String,
        val manifestFile: File?,
        val dir: File,
        val realPath: Path,
    )

    /** One path a deployment writes to or deletes through one tool, with the directory it has to lie in when a user deployment replaces it. */
    private data class DeployTarget(
        val path: File,
        val action: DeployAction,
        val toolType: ToolType,
        val deployedBy: String,
        val containment: Containment? = null,
    )

    /** The directory a replaced path of a user deployment has to lie in, and the artifact it is replaced for, as the export names it. */
    private data class Containment(
        val owned: File,
        val describedBy: String,
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
 * @property link the symbolic link whose destination overlaps the source folder: [path] itself when a replaced [path] is a link, otherwise one below [path]; `null` when [path] itself, where the run writes or deletes it, overlaps the source folder
 * @property overlap how [path], or the destination of [link], lies relative to the source folder
 * @property toolType the tool whose layout [path] belongs to
 * @property deployedBy the project or user deployment the path belongs to, as a message names it
 * @property unlinked the replaced symbolic link, [path] itself or one above it, whose removal by the deploy places [path] where it overlaps the source folder; `null` when [path] overlaps it without that
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
    val unlinked: File? = null,
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
            // The delete of a replacing deploy never opens what a replaced directory that is a link, or a link below it, leads to, so for those cases the message says where the link leads instead of claiming the source would be damaged.
            if (action == DeployAction.Replace && link?.path == path) {
                return "$head, and '${path.absolutePath}' is itself a link to '${link.destination}', which ${overlap.description} that folder. " +
                    "A replacing deploy never opens what it leads to, but it refuses while a replaced directory links into a source folder. " +
                    "Remove the link '${path.absolutePath}', or turn off 'replace' for that deployment."
            }
            if (action == DeployAction.Replace && link != null) {
                return "$head, and the link '${link.path.absolutePath}' below the directory to be replaced leads to '${link.destination}', which ${overlap.description} that folder. " +
                    "The deploy would only unlink it, but it refuses while a link below a replaced directory leads into a source folder. " +
                    "Remove the link '${link.path.absolutePath}', or turn off 'replace' for that deployment."
            }
            val how = when {
                link != null -> "and the link '${link.path.absolutePath}' in it leads to '${link.destination}', which ${overlap.description} that folder"
                unlinked != null -> "which ${overlap.description} that folder once the link '${unlinked.absolutePath}' is removed"
                else -> "which ${overlap.description} that folder once links are resolved"
            }
            // Removing that link or deselecting the skill leaves the path where it is, inside the source folder, so only moving the deploy directory or keeping the link helps.
            val advice = when {
                link == null && unlinked != null -> "Move the directory that $deployedBy deploys to out of the source folder, or turn off 'replace' for that deployment."
                action is DeployAction.WriteSkill -> "Remove the link or folder that leads there, or deselect the skill '${action.skillId}' for that deployment."
                else -> "Move the source folder, or remove the link to it, so that it neither is, contains, nor lies inside '${path.absolutePath}', or turn off 'replace' for that deployment."
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
