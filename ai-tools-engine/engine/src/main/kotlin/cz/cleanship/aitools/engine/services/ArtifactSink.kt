package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.io.DiscardingOutput
import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.io.OutputStreamOutput
import cz.cleanship.aitools.engine.io.checkArtifactDirectoryWithin
import cz.cleanship.aitools.engine.io.deleteArtifactDirectoryWithin
import cz.cleanship.aitools.engine.models.VersionedManifest
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Where the artifacts of a run end up: on disk in a deploy, nowhere in a dry run.
 *
 * It is the only thing that separates the two kinds of run. [ExportService] resolves and validates exactly the same
 * way for both and hands the sink a target that is already known to be legitimate, so a dry run reports every
 * failure a deploy would - a printer that cannot resolve a reference, a companion file that does not exist, an
 * artifact directory outside its scope - and differs from a deploy in nothing but the writes.
 */
interface ArtifactSink {

    /**
     * Renders [entity] through [outputConsumer] into [targetFile], or only renders it.
     *
     * @throws Exception whatever [outputConsumer] throws
     */
    fun <T : VersionedManifest> export(entity: T, targetFile: File, outputConsumer: (Output) -> Unit)

    /**
     * Copies the companion file [sourceFile], which exists, onto [targetFile], or only says that it would.
     */
    fun copySkillFile(sourceFile: File, targetFile: File)

    /**
     * Removes [artifactDir] and everything under it before it is written again, or only says that it would. Both
     * refuse a directory that is not inside [owned] - see
     * [cz.cleanship.aitools.engine.io.deleteArtifactDirectoryWithin].
     *
     * @throws cz.cleanship.aitools.engine.io.ArtifactPathException if [artifactDir] is not inside [owned]
     */
    fun replaceArtifactDirectory(artifactDir: File, owned: File, describedBy: String)
}

/**
 * Writes the artifacts of a deploy to disk.
 */
object FileSystemArtifactSink : ArtifactSink {

    /**
     * Exports [entity] to [targetFile] atomically.
     *
     * Content resolution happens inside [outputConsumer], so it can fail half-way through writing. To make sure a
     * failure never ships a truncated artifact, the content is written to a temporary file in the target directory
     * first and only moved onto [targetFile] once [outputConsumer] has completed successfully. On failure the
     * temporary file is removed and any previously exported [targetFile] is left untouched.
     *
     * @throws Exception whatever [outputConsumer] throws, after the temporary file has been cleaned up
     */
    override fun <T : VersionedManifest> export(entity: T, targetFile: File, outputConsumer: (Output) -> Unit) {
        val targetDir = targetFile.parentFile
        targetDir.mkdirs()
        val temporaryFile = File.createTempFile("${targetFile.name}.", ".tmp", targetDir)
        try {
            OutputStreamOutput(FileOutputStream(temporaryFile)).use(outputConsumer)
            Files.move(temporaryFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            // No-op once the move above succeeded, cleans up the partial write otherwise.
            temporaryFile.delete()
        }
        LOG.info("Exported ${entity.javaClass.simpleName} ${entity.id} to ${targetFile.absolutePath}")
    }

    override fun copySkillFile(sourceFile: File, targetFile: File) {
        targetFile.parentFile.mkdirs()
        sourceFile.copyTo(targetFile, overwrite = true)
        LOG.info("Copied skill file {} to {}", sourceFile.absolutePath, targetFile.absolutePath)
    }

    override fun replaceArtifactDirectory(artifactDir: File, owned: File, describedBy: String) =
        artifactDir.deleteArtifactDirectoryWithin(owned, describedBy)

    private val LOG = LoggerFactory.getLogger(FileSystemArtifactSink::class.java)
}

/**
 * Renders every artifact of a dry run and writes none of it, naming instead what a deploy would have written.
 *
 * Every line names the absolute target path, so that the transcript of a dry run reads as the plan of the deploy
 * it stands in for. Nothing is created on the way there either: not the directory an artifact would land in, and
 * not the temporary file a deploy writes through.
 */
object DryRunArtifactSink : ArtifactSink {

    override fun <T : VersionedManifest> export(entity: T, targetFile: File, outputConsumer: (Output) -> Unit) {
        DiscardingOutput.use(outputConsumer)
        LOG.info("Would export ${entity.javaClass.simpleName} ${entity.id} to ${targetFile.absolutePath}")
    }

    override fun copySkillFile(sourceFile: File, targetFile: File) {
        LOG.info("Would copy skill file {} to {}", sourceFile.absolutePath, targetFile.absolutePath)
    }

    override fun replaceArtifactDirectory(artifactDir: File, owned: File, describedBy: String) {
        artifactDir.checkArtifactDirectoryWithin(owned, describedBy)
        LOG.info("Would remove '{}' for {} before writing it again", artifactDir.absolutePath, describedBy)
    }

    private val LOG = LoggerFactory.getLogger(DryRunArtifactSink::class.java)
}
