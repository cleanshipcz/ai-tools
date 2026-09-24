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
import java.nio.file.attribute.PosixFilePermissions

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
     * Writes [content] to the config file [targetFile], replacing what it held, or only says that it would.
     *
     * A config file is one the user owns alongside the engine: an existing one keeps its permission bits. [targetFile] is the file itself, never a symbolic link; the caller decides where a link at a config path may lead.
     *
     * @param describedBy what [content] holds, as the log line names it
     */
    fun writeConfigFile(targetFile: File, content: String, describedBy: String)

    /**
     * Removes [artifactDir] and everything under it before it is written again, or only says that it would. Both refuse a directory that is not inside [owned] - see [cz.cleanship.aitools.engine.io.deleteArtifactDirectoryWithin].
     *
     * @throws cz.cleanship.aitools.engine.io.ArtifactPathException if [artifactDir] is not inside [owned]
     * @throws cz.cleanship.aitools.engine.io.ArtifactDeleteException if an entry below [artifactDir] cannot be deleted, which leaves it partly deleted; a sink that only says it would remove [artifactDir] never throws it
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
        writeAtomically(targetFile) { OutputStreamOutput(FileOutputStream(it)).use(outputConsumer) }
        LOG.info("Exported ${entity.javaClass.simpleName} ${entity.id} to ${targetFile.absolutePath}")
    }

    /**
     * Writes [content] to [targetFile] atomically, the way [export] writes an artifact. An existing file keeps its permission bits: the temporary file is created with them, before any content is written into it.
     */
    override fun writeConfigFile(targetFile: File, content: String, describedBy: String) {
        val permissions = targetFile.takeIf { it.exists() }?.let { runCatching { Files.getPosixFilePermissions(it.toPath()) }.getOrNull() }
        targetFile.parentFile.mkdirs()
        val temporaryFile = if (permissions == null) {
            File.createTempFile("${targetFile.name}.", ".tmp", targetFile.parentFile).toPath()
        } else {
            Files.createTempFile(targetFile.parentFile.toPath(), "${targetFile.name}.", ".tmp", PosixFilePermissions.asFileAttribute(permissions))
        }
        try {
            // The attribute of createTempFile is narrowed by the umask, so the bits are set once more, still before the content is written.
            permissions?.let { Files.setPosixFilePermissions(temporaryFile, it) }
            Files.writeString(temporaryFile, content)
            Files.move(temporaryFile, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
        LOG.info("Wrote {} to {}", describedBy, targetFile.absolutePath)
    }

    private fun writeAtomically(targetFile: File, write: (File) -> Unit) {
        val targetDir = targetFile.parentFile
        targetDir.mkdirs()
        val temporaryFile = File.createTempFile("${targetFile.name}.", ".tmp", targetDir)
        try {
            write(temporaryFile)
            Files.move(temporaryFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            // No-op once the move above succeeded, cleans up the partial write otherwise.
            temporaryFile.delete()
        }
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

    override fun writeConfigFile(targetFile: File, content: String, describedBy: String) {
        LOG.info("Would write {} to {}", describedBy, targetFile.absolutePath)
    }

    override fun replaceArtifactDirectory(artifactDir: File, owned: File, describedBy: String) {
        artifactDir.checkArtifactDirectoryWithin(owned, describedBy)
        LOG.info("Would remove '{}' for {} before writing it again", artifactDir.absolutePath, describedBy)
    }

    private val LOG = LoggerFactory.getLogger(DryRunArtifactSink::class.java)
}
