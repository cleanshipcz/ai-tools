package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.io.DiscardingOutput
import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.io.OutputStreamOutput
import cz.cleanship.aitools.engine.io.checkArtifactDirectoryWithin
import cz.cleanship.aitools.engine.io.deleteArtifactDirectoryWithin
import cz.cleanship.aitools.engine.models.VersionedManifest
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions

/**
 * Where the artifacts of a run end up: on disk in a deploy, nowhere in a dry run.
 *
 * It is the only thing that separates the two kinds of run. [ExportService] resolves and validates exactly the same way for both and hands the sink a target that is already known to be legitimate, so a dry run reports every failure a deploy would - a printer that cannot resolve a reference, a companion file that does not exist or cannot be read, an artifact directory outside its scope - and differs from a deploy in nothing but the writes. A write the file system refuses is therefore found only by a deploy: a dry run finds a tool directory that is a dangling or looping link, or not a directory, only through the MCP config file in it, which is checked before anything is written, and a read-only directory not at all.
 */
interface ArtifactSink {

    /**
     * Renders [entity] through [outputConsumer] into [targetFile], or only renders it.
     *
     * @throws ArtifactWriteException naming [targetFile] if it or the directory holding it cannot be written; a sink that only renders never throws it
     * @throws Exception whatever else [outputConsumer] throws
     */
    fun <T : VersionedManifest> export(entity: T, targetFile: File, outputConsumer: (Output) -> Unit)

    /**
     * Copies the companion file [sourceFile], which exists, onto [targetFile], or only says that it would.
     *
     * @throws SkillFileResolvingException naming [sourceFile] if it cannot be read, which leaves [targetFile] as it was; a sink that only says it would copy never throws it
     * @throws ArtifactWriteException naming [targetFile] if it or the directory holding it cannot be written; a sink that only says it would copy never throws it
     */
    fun copySkillFile(sourceFile: File, targetFile: File)

    /**
     * Writes [content] to the config file [targetFile], replacing what it held, or only says that it would.
     *
     * A config file is one the user owns alongside the engine: an existing one keeps its permission bits. [targetFile] is the file itself, never a symbolic link; the caller decides where a link at a config path may lead.
     *
     * @param describedBy what [content] holds, as the log line names it
     * @throws java.io.IOException if [targetFile] or the directory holding it cannot be written, which leaves [targetFile] as it was; a sink that only says it would write never throws it
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
     * @throws ArtifactWriteException naming [targetFile] if it or the directory holding it cannot be written
     * @throws Exception whatever else [outputConsumer] throws, after the temporary file has been cleaned up
     */
    override fun <T : VersionedManifest> export(entity: T, targetFile: File, outputConsumer: (Output) -> Unit) {
        writingTo(targetFile) { writeAtomically(targetFile) { OutputStreamOutput(FileOutputStream(it)).use(outputConsumer) } }
        LOG.info("Exported ${entity.javaClass.simpleName} ${entity.id} to ${targetFile.absolutePath}")
    }

    /**
     * Writes [content] to [targetFile] atomically, the way [export] writes an artifact. An existing file keeps its permission bits: the temporary file is created with them, before any content is written into it.
     */
    override fun writeConfigFile(targetFile: File, content: String, describedBy: String) {
        val permissions = targetFile.takeIf { it.exists() }?.let { runCatching { Files.getPosixFilePermissions(it.toPath()) }.getOrNull() }
        targetFile.parentFile.mkdirs()
        val temporaryFile = if (permissions == null) {
            File.createTempFile(temporaryPrefix(targetFile), ".tmp", targetFile.parentFile).toPath()
        } else {
            Files.createTempFile(targetFile.parentFile.toPath(), temporaryPrefix(targetFile), ".tmp", PosixFilePermissions.asFileAttribute(permissions))
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

    // File.createTempFile refuses a prefix of fewer than three characters, so a short name such as 'a' is padded; it keeps File.createTempFile, whose file takes the default permissions an artifact is written with, where Files.createTempFile would make it readable by its owner only.
    private fun temporaryPrefix(targetFile: File): String = "${targetFile.name}.".padEnd(MIN_TEMPORARY_PREFIX, '_')

    private fun writeAtomically(targetFile: File, write: (File) -> Unit) {
        val targetDir = targetFile.parentFile
        targetDir.mkdirs()
        val temporaryFile = File.createTempFile(temporaryPrefix(targetFile), ".tmp", targetDir)
        try {
            write(temporaryFile)
            Files.move(temporaryFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            // No-op once the move above succeeded, cleans up the partial write otherwise.
            temporaryFile.delete()
        }
    }

    /**
     * Copies [sourceFile] onto [targetFile] atomically, the way [export] writes an artifact, so a copy that fails at any point leaves the earlier [targetFile] as it was.
     */
    override fun copySkillFile(sourceFile: File, targetFile: File) {
        // A failure to open or to read the source names the source, even in the middle of the copy, so it is never reported as a target that cannot be written.
        val source = try {
            Files.newInputStream(sourceFile.toPath())
        } catch (ex: IOException) {
            throw sourceUnreadable(sourceFile, ex)
        }
        source.use { input ->
            writingTo(targetFile) {
                writeAtomically(targetFile) { temporaryFile ->
                    FileOutputStream(temporaryFile).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = try {
                                input.read(buffer)
                            } catch (ex: IOException) {
                                throw sourceUnreadable(sourceFile, ex)
                            }
                            if (read < 0) break
                            output.write(buffer, 0, read)
                        }
                    }
                }
            }
        }
        LOG.info("Copied skill file {} to {}", sourceFile.absolutePath, targetFile.absolutePath)
    }

    private fun sourceUnreadable(sourceFile: File, ex: IOException) =
        SkillFileResolvingException("Skill file '${sourceFile.absolutePath}' cannot be read (${ex.failureDescription()}). Make it readable, or remove it from 'files'.", ex)

    /**
     * Runs [write], naming [targetFile] in any [IOException] it throws, which on its own often names neither the file nor its directory.
     */
    private fun writingTo(targetFile: File, write: () -> Unit) = try {
        write()
    } catch (ex: IOException) {
        throw ArtifactWriteException(targetFile, ex)
    }

    override fun replaceArtifactDirectory(artifactDir: File, owned: File, describedBy: String) =
        artifactDir.deleteArtifactDirectoryWithin(owned, describedBy)

    private const val MIN_TEMPORARY_PREFIX = 3

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

/**
 * Thrown when the artifact [targetFile] cannot be written, naming it, the class of the failure that stopped the write and the reason the operating system gave, never content.
 */
// An IOException, so a caller that lets a failed write propagate, such as the user-scope export, handles it like any other failed write.
class ArtifactWriteException(
    val targetFile: File,
    cause: IOException,
) : IOException("'${targetFile.absolutePath}' cannot be written (${cause.failureDescription()})", cause)

/**
 * Returns the class of this failure, followed by the reason the operating system gave for it when one is known, such as `NotDirectoryException` or `IOException: Not a directory`; a path or any other text of the message is never included.
 */
internal fun IOException.failureDescription(): String = osReason()?.let { "${javaClass.simpleName}: $it" } ?: javaClass.simpleName

// A FileSystemException carries the reason apart from its paths; a FileNotFoundException ends its message with it in parentheses; a plain IOException of the JDK file code is the reason alone. Anything that could be a path or quote a value is dropped.
private fun IOException.osReason(): String? = when {
    this is FileSystemException -> reason
    this is FileNotFoundException -> message?.let { TRAILING_REASON.find(it)?.groupValues?.get(1) }
    javaClass == IOException::class.java -> message
    else -> null
}?.takeIf { PLAIN_REASON.matches(it) }

private val TRAILING_REASON = Regex("""\(([^()]+)\)$""")

private val PLAIN_REASON = Regex("""[A-Za-z][A-Za-z ,-]*""")
