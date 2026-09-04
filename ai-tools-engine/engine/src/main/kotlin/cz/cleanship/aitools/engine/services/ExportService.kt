package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.SkillFile
import cz.cleanship.aitools.engine.models.VersionedManifest
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Puts the artifacts of a run where they belong, after checking that they belong there.
 *
 * Everything that decides whether a write is legitimate lives here - where a companion file comes from, where it
 * may land, whether an artifact directory is inside the scope that owns it - and everything that touches the disk
 * lives in the [sink]. A dry run therefore reuses this service unchanged with a sink that writes nothing, and still
 * fails on exactly the manifests a deploy fails on.
 *
 * @param sink where the artifacts go: [FileSystemArtifactSink] deploys, [DryRunArtifactSink] only reports
 */
class ExportService(
    private val sink: ArtifactSink = FileSystemArtifactSink,
) {

    /**
     * Exports [entity] to [targetFile] through the sink of this service - see [ArtifactSink.export].
     *
     * @throws Exception whatever [outputConsumer] throws
     */
    fun <T : VersionedManifest> export(
        entity: T,
        targetFile: File,
        outputConsumer: (Output) -> Unit,
    ) = sink.export(entity, targetFile, outputConsumer)

    /**
     * Copies every companion file declared by a skill next to its generated manifest.
     *
     * @throws SkillFileResolvingException if a declared file cannot be resolved, does not exist, or would land
     * outside [targetDir] - see [resolveTarget]
     */
    fun copySkillFiles(
        skillFiles: List<SkillFile>,
        sourceDir: File?,
        targetDir: File,
        skillId: String = "",
    ) {
        for (skillFile in skillFiles) {
            val sourceFile = resolveSource(skillFile.source, sourceDir)
            warnWhenSourceComesFromOutside(skillFile.source, sourceFile, sourceDir, skillId)
            val targetFile = resolveTarget(skillFile.target, targetDir)
            requireSourceExists(sourceFile)
            sink.copySkillFile(sourceFile, targetFile)
        }
    }

    /**
     * Removes [artifactDir] before it is written again, refusing one that is not inside [owned] - see
     * [ArtifactSink.replaceArtifactDirectory].
     *
     * @throws cz.cleanship.aitools.engine.io.ArtifactPathException if [artifactDir] is not inside [owned]
     */
    fun replaceArtifactDirectory(artifactDir: File, owned: File, describedBy: String) =
        sink.replaceArtifactDirectory(artifactDir, owned, describedBy)

    /**
     * Names a companion file that comes from somewhere other than the directory of the skill declaring it.
     *
     * An absolute `source`, and a relative one climbing out with `..`, are both supported and stay supported: sharing
     * one reference file between skills is what they are for. They are worth saying out loud all the same, because
     * the directory such a file is copied into is `<skills>/<id>/` - in the user scope, a directory inside the home
     * whose whole purpose is to be read into an agent's context. A run should not have to be reconstructed from the
     * manifests to see that it pulled a file in from elsewhere.
     */
    private fun warnWhenSourceComesFromOutside(declared: String, sourceFile: File, sourceDir: File?, skillId: String) {
        val owned = sourceDir?.toPath()?.toAbsolutePath()?.normalize() ?: return
        val resolved = sourceFile.toPath().toAbsolutePath().normalize()
        if (resolved.startsWith(owned)) return
        LOG.warn(
            "Skill '{}' copies '{}' from outside its own directory: {}",
            skillId,
            declared,
            resolved,
        )
    }

    /**
     * Resolves the declared [target] of a companion file against [targetDir], refusing one that would leave it.
     *
     * `target` is free-form manifest text, and the directory it resolves against is the skill directory of whatever
     * scope is being deployed - inside a project, or inside the user's home. A value climbing out of it with `..`
     * would let a skill manifest write anywhere the process can, so it is rejected rather than resolved. Nesting
     * further in is left alone, because `templates/example.txt` is how the existing manifests ship their files.
     *
     * The comparison is on the normalized paths rather than the canonical ones: the target does not exist yet, and
     * its parents usually do not either, so there is nothing on disk to canonicalize against.
     */
    private fun resolveTarget(target: String, targetDir: File): File {
        val resolved = targetDir.resolve(target).toPath().normalize()
        val owned = targetDir.toPath().normalize()
        if (resolved == owned || !resolved.startsWith(owned)) {
            throw SkillFileResolvingException(
                "Skill file target '$target' would be written outside the skill directory " +
                    "'${targetDir.absolutePath}'. Declare a target inside the skill.",
            )
        }
        return resolved.toFile()
    }

    /**
     * Checked here rather than left to the copy itself, so that a dry run - which copies nothing - reports a
     * declared file that was never written exactly like a deploy does.
     */
    private fun requireSourceExists(sourceFile: File) {
        if (!sourceFile.exists()) {
            throw SkillFileResolvingException(
                "Skill file '${sourceFile.absolutePath}' does not exist. " +
                    "Add the file next to the skill manifest or remove it from 'files'.",
            )
        }
    }

    private fun resolveSource(source: String, sourceDir: File?): File {
        val file = File(source)
        if (file.isAbsolute) return file
        if (sourceDir == null) {
            throw SkillFileResolvingException(
                "Cannot resolve relative skill file '$source' without a source directory. " +
                    "Use a directory-based skill or provide an absolute path.",
            )
        }
        return sourceDir.resolve(source)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ExportService::class.java)
    }
}

/**
 * Thrown when a companion file declared by a skill manifest cannot be copied, because the manifest points at a
 * file that cannot be resolved or does not exist. Like the resolver failures, this is an authoring error the
 * manifest author fixes, so it fails a single manifest instead of the whole run.
 */
class SkillFileResolvingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
