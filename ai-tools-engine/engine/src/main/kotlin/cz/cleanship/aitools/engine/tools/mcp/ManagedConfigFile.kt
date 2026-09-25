package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.io.TargetRoot
import cz.cleanship.aitools.engine.io.linkTarget
import cz.cleanship.aitools.engine.services.ConfigFileChangedException
import cz.cleanship.aitools.engine.services.ConfigFileState
import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.services.NewConfigFileMode
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * A file the engine edits in place beside the user, such as an MCP config file, a `settings.json` or the MCP ledger, reached only where [root] allows - see [TargetRoot].
 */
internal class ManagedConfigFile(
    val file: File,
    private val root: TargetRoot,
) {

    /**
     * Returns the file to read and write: [file] itself, or the real target of a symbolic link at [file] that [root] admits. The directory holding [file] must be one [root] admits once links are resolved too, and nothing at the path is opened before it is known to be a regular file.
     *
     * @throws McpConfigFileException naming [file], never quoting its content, if a link at [file] or at a directory above it cannot be followed or leads where [root] does not admit, if the nearest existing entry above [file] is not a directory, or if [file] exists but is not a regular file
     */
    fun target(): File {
        requireDirectoryAdmitted()
        val path = file.toPath()
        val target = if (Files.isSymbolicLink(path)) admittedLinkTarget(path) else path
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            // A FIFO would block the run on open, a device could be read without end, and a directory cannot be merged.
            throw McpConfigFileException("'${file.absolutePath}' is not a regular file, so the engine leaves it untouched. Remove what is at that path, and deploy again.")
        }
        return if (target == path) file else target.toFile()
    }

    /**
     * Returns the content of [target], or `null` when it does not exist.
     *
     * @throws McpConfigFileException naming [file], never quoting its content, if [target] cannot be read or is not valid UTF-8
     */
    fun read(target: File): String? = try {
        if (target.exists()) decode(Files.readAllBytes(target.toPath())) else null
    } catch (ex: IOException) {
        throw McpConfigFileException("'${file.absolutePath}' cannot be read (${ex.javaClass.simpleName}), so the engine leaves it untouched.", ex)
    }

    /**
     * Writes [content] to [target] through [exportService], only while [target] still holds [previous], or does not exist when it is `null`; a dry run only logs it. A file created in the home is readable and writable by its owner only, and one created in a project gets the mode of every artifact.
     *
     * The sink compares [target] with [previous] right before it moves the new content into place - see [cz.cleanship.aitools.engine.services.ArtifactSink.writeConfigFile]. Together with [requireUnchanged] after a merge, this refuses a file another program, such as a running Claude Code, wrote since the engine read it.
     *
     * @param describedBy what [content] holds, as the log line names it
     * @throws McpConfigFileException naming [file] if [target] no longer holds [previous], or cannot be written
     */
    fun write(exportService: ExportService, target: File, content: String, describedBy: String, previous: String?) {
        changing { exportService.writeConfigFile(target, content, describedBy, ConfigFileState.of(previous), createdAs = newFileMode()) }
    }

    /**
     * Deletes [target] through [exportService], only while it still holds [previous]; a dry run only logs it.
     *
     * @param describedBy what [target] holds, as the log line names it
     * @throws McpConfigFileException naming [file] if [target] no longer holds [previous], or cannot be deleted
     */
    fun delete(exportService: ExportService, target: File, describedBy: String, previous: String) {
        changing { exportService.deleteConfigFile(target, describedBy, ConfigFileState.Exactly(previous)) }
    }

    /**
     * Fails when the content of [target] is no longer [expected], because a tool wrote it after the engine read it.
     *
     * @param expected the content read before, or `null` when [target] did not exist
     * @throws McpConfigFileException naming [file] if [target] holds anything but [expected], cannot be read, or is not valid UTF-8
     */
    fun requireUnchanged(target: File, expected: String?) {
        if (read(target) != expected) throw changed()
    }

    private fun changing(change: () -> Unit) = try {
        change()
    } catch (ex: ConfigFileChangedException) {
        throw changed(ex)
    } catch (ex: IOException) {
        // A directory that cannot be created or a file that cannot be replaced is a problem of this file, like one that cannot be read, and must not stop the deployments after it.
        throw McpConfigFileException("'${file.absolutePath}' cannot be written (${ex.javaClass.simpleName}), so the engine leaves it untouched.", ex)
    }

    // The files of the home are where a tool keeps its session state and possibly a key, beside what the engine writes; a file of a project is one the project may commit, and is created as every artifact is.
    private fun newFileMode(): NewConfigFileMode = when (root) {
        is TargetRoot.Project -> NewConfigFileMode.DEFAULT
        is TargetRoot.UserHome -> NewConfigFileMode.OWNER_ONLY
    }

    private fun changed(cause: Throwable? = null) =
        McpConfigFileException("'${file.absolutePath}' changed while the engine merged it, so the engine leaves it untouched. Deploy again once the tool writing it is idle.", cause)

    /**
     * Returns [bytes] as text, refusing bytes that are not UTF-8: decoding them leniently would replace them, and writing the file back would change bytes the engine does not own.
     */
    private fun decode(bytes: ByteArray): String = try {
        Charsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (ex: CharacterCodingException) {
        throw McpConfigFileException("'${file.absolutePath}' is not valid UTF-8, so the engine leaves it untouched rather than rewrite it with bytes of its own.", ex)
    }

    /**
     * Fails when the directory holding [file], or its nearest ancestor that exists as an entry, a link included, is not one [root] admits once links are resolved, as a linked `.codex` or `.vscode` of a dotfile setup inside a project would be; is a link that cannot be followed; or is not a directory, so the directory of [file] could not be created.
     */
    private fun requireDirectoryAdmitted() {
        // A root that does not exist yet holds no link, and every folder below it is created by the deploy itself.
        if (!root.directory.exists()) return
        // A link that leads nowhere or loops does not exist once followed, so the entry itself is looked for: resolving it then fails here, in a dry run as in a deploy, instead of when the deploy creates the file.
        val existing = generateSequence(file.absoluteFile.parentFile) { it.parentFile }.first { Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS) }
        val (directory, realRoot) = realPaths(existing.toPath())
        if (!root.admits(directory, realRoot)) {
            throw McpConfigFileException(
                "'${file.absolutePath}' lies in '$directory' once links are resolved, outside the project directory '$realRoot'. The engine writes only inside the project, so it leaves the file untouched.",
            )
        }
        // Found before the write, a file where a directory belongs fails a dry run exactly as it fails the deploy.
        if (!Files.isDirectory(directory)) {
            throw McpConfigFileException("'${file.absolutePath}' lies below '$directory', which is not a directory, so the engine leaves it untouched. Remove what is at that path, and deploy again.")
        }
    }

    private fun realPaths(path: Path): Pair<Path, Path> = try {
        path.toRealPath() to root.directory.toPath().toRealPath()
    } catch (ex: IOException) {
        // A link that leads nowhere, loops or is too long fails this file only, like every other problem of the file.
        throw McpConfigFileException("'${file.absolutePath}' is reached through ${unfollowableLink(path)} cannot be followed (${ex.javaClass.simpleName}), so the engine leaves it untouched.", ex)
    }

    /**
     * Returns the subject of a sentence naming the symbolic link nearest to [path], among [path] and the directories above it, and where it leads, or only that it is a link when none can be read.
     */
    private fun unfollowableLink(path: Path): String {
        val link = generateSequence(path.toAbsolutePath()) { it.parent }.firstOrNull { Files.isSymbolicLink(it) }
        val leadsTo = link?.let(::linkTarget)
        return if (link == null || leadsTo == null) "a symbolic link that" else "the symbolic link '$link', which leads to '$leadsTo' and"
    }

    private fun admittedLinkTarget(path: Path): Path {
        val (target, realRoot) = realPaths(path)
        if (!root.admits(target, realRoot)) {
            throw McpConfigFileException(
                "'${file.absolutePath}' is a symbolic link to '$target', which lies outside the project directory '$realRoot'. The engine writes through a link only inside the project, so it leaves both untouched.",
            )
        }
        return target
    }
}
