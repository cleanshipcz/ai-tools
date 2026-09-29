package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.models.McpText
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.UserPrincipal

/**
 * The launcher `scripts/mcp-launch` of the ai-tools repository [checkout], and whether a tool may be given it to start.
 *
 * @param checkout the ai-tools repository the engine runs from, the directory holding its `config.yml`
 * @param ownership reports the owner and the permissions of the launcher, and the user running the engine
 */
class McpLauncherFile(
    checkout: File,
    private val ownership: LauncherFileOwnership = LauncherFileOwnership.SYSTEM,
) {
    private val repository: Path = checkout.toPath().toAbsolutePath().normalize()
    private val path: Path = repository.resolve(LAUNCHER_PATH)

    /**
     * Returns the absolute real path of the launcher when a tool may start it: a regular file inside [repository] once every link is resolved, owned by the user running the engine, executable by that user, writable by no one else, in directories that, from its own up to the real root of [repository], are each owned by that user and writable by no one else, and with a real path free of `${`, which a tool would expand; otherwise the problem, naming the launcher, and the directory when one is the cause, and never a value.
     */
    fun check(): State {
        val problem = when {
            !Files.exists(path) -> "does not exist"
            !Files.isRegularFile(path) -> "is not a regular file"
            !Files.isExecutable(path) -> "is not executable"
            else -> null
        }
        if (problem != null) return unusable("'$path', which $problem. Restore it in the ai-tools repository, $REMEDY")
        val real = try {
            path.toRealPath()
        } catch (ex: IOException) {
            return unusable("'$path', whose real path cannot be read (${ex.javaClass.simpleName}). Restore it in the ai-tools repository, $REMEDY")
        }
        if (McpText.REFERENCE_OPENER in real.toString()) {
            return unusable(
                "'$real', whose path holds '${McpText.REFERENCE_OPENER}', which a tool would expand. Move the ai-tools repository to a path without it, or set 'secrets_manager: environment' in config.local.yml.",
            )
        }
        val realRepository = try {
            repository.toRealPath()
        } catch (ex: IOException) {
            return unusable("'$path', but the real path of the ai-tools repository '$repository' cannot be read (${ex.javaClass.simpleName}). Make the repository readable, $REMEDY")
        }
        if (!real.startsWith(realRepository)) {
            return unusable("'$path', which resolves to '$real', outside the ai-tools repository '$realRepository'. Restore $LAUNCHER_PATH of the repository, $REMEDY")
        }
        return (ownershipProblem(real) ?: directoryProblem(real, realRepository))?.let { unusable(it) } ?: State.Usable(real.toString())
    }

    private fun ownershipProblem(real: Path): String? {
        val (attributes, user) = attributesAndUser(real) { ex -> return cannotTell(real, "it", ex) }
        if (attributes.owner() != user) {
            return "'$real', which is owned by '${attributes.owner().name}', not by '${user.name}', who runs the engine. Make '${user.name}' its owner, $REMEDY"
        }
        val writers = attributes.otherWriters() ?: return null
        return "'$real', which $writers can write, so the file every tool starts could be changed by someone else. Remove that permission with: chmod go-w '$real', $REMEDY"
    }

    // Whoever may write a directory on the way may rename another file over the launcher, or another directory over one of its directories, without touching the launcher's own mode. A directory above the repository is not checked: whoever may write there may replace the whole repository.
    private fun directoryProblem(real: Path, realRepository: Path): String? {
        for (directory in generateSequence(real.parent) { it.parent }.takeWhile { it.startsWith(realRepository) }) {
            val (attributes, user) = attributesAndUser(directory) { ex -> return cannotTell(real, "its directory '$directory'", ex) }
            if (attributes.owner() != user) {
                return "'$real', whose directory '$directory' is owned by '${attributes.owner().name}', not by '${user.name}', who runs the engine. Make '${user.name}' its owner, $REMEDY"
            }
            val writers = attributes.otherWriters() ?: continue
            return "'$real', whose directory '$directory' $writers can write, so the file every tool starts could be replaced by someone else. Remove that permission with: chmod go-w '$directory', $REMEDY"
        }
        return null
    }

    // An owner or a permission that cannot be read cannot be trusted: every tool of every deployed project starts this file.
    private inline fun attributesAndUser(
        path: Path,
        unknown: (Exception) -> Nothing,
    ): Pair<PosixFileAttributes, UserPrincipal> = try {
        ownership.attributesOf(path) to ownership.runningUser()
    } catch (ex: IOException) {
        unknown(ex)
    } catch (ex: UnsupportedOperationException) {
        unknown(ex)
    }

    // Who besides the owner may write, as the subject of a sentence, or null when no one may.
    private fun PosixFileAttributes.otherWriters(): String? = listOfNotNull(
        "its group".takeIf { PosixFilePermission.GROUP_WRITE in permissions() },
        "others".takeIf { PosixFilePermission.OTHERS_WRITE in permissions() },
    ).takeIf { it.isNotEmpty() }?.joinToString(" and ")

    private fun cannotTell(real: Path, subject: String, ex: Exception) =
        "'$real', but the engine cannot tell who owns $subject and who may write it (${ex.javaClass.simpleName}), so it does not give it to any tool. " +
            "Keep the ai-tools repository on a file system with POSIX owners and permissions, $REMEDY"

    private fun unusable(launcher: String) = State.Unusable("reads its secrets through the launcher $launcher")

    /**
     * Whether the launcher can be rendered.
     */
    sealed interface State {

        /** The launcher may be started by its absolute real path [path]. */
        data class Usable(val path: String) : State

        /**
         * The launcher may not be started.
         *
         * @property problem why, as the end of a sentence about the server that needs the launcher, naming the launcher and what to do
         */
        data class Unusable(val problem: String) : State
    }

    companion object {
        /** Where the launcher lies in the ai-tools repository. */
        const val LAUNCHER_PATH = "scripts/mcp-launch"

        private const val REMEDY = "or set 'secrets_manager: environment' in config.local.yml to read every secret from the environment of the tool."
    }
}
