package cz.cleanship.aitools.engine.io

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * The directory one deployment writes into, and how far a symbolic link below it may lead.
 */
sealed interface TargetRoot {

    /** The directory itself: a project directory, or the home the user scope is deployed under. */
    val directory: File

    /**
     * Returns whether [realPath], a path whose links are resolved, is one this deployment may write, given [realRoot], [directory] with its links resolved.
     */
    fun admits(realPath: Path, realRoot: Path): Boolean

    /**
     * A project directory: a link below it must lead inside it once resolved, so a deploy never writes into the repository or home around a project.
     */
    data class Project(override val directory: File) : TargetRoot {
        override fun admits(realPath: Path, realRoot: Path): Boolean = realPath.startsWith(realRoot)
    }

    /**
     * The home of the user scope: a link below it may lead anywhere, as the links of a dotfile repository do, but must lead to something.
     */
    data class UserHome(override val directory: File) : TargetRoot {
        override fun admits(realPath: Path, realRoot: Path): Boolean = true
    }
}

/**
 * Fails when [toolDirectory], a directory of this root that a tool writes its files into, such as `.codex`, cannot hold them: a symbolic link that leads nowhere or in a loop, something other than a directory, or, in a project, a link that leads outside it. A directory that does not exist yet passes, since the deploy creates it.
 *
 * It reads the file system only, so a dry run fails exactly where a deploy fails.
 *
 * @param writes what the failure stops the engine from writing, such as `codex files of project 'x'`, as the message names it
 * @throws ToolDirectoryException naming [toolDirectory], where a link at it leads, and [writes]
 */
fun TargetRoot.requireToolDirectory(toolDirectory: File, writes: String) {
    val path = toolDirectory.absoluteFile.toPath()
    // A root that does not exist yet holds no link, and every directory below it is created by the deploy itself.
    if (!directory.exists() || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
    val real = try {
        path.toRealPath()
    } catch (ex: IOException) {
        val leadsTo = if (Files.isSymbolicLink(path)) linkTarget(path)?.let { " to '$it'" }.orEmpty() else ""
        throw ToolDirectoryException(
            "'$path' is a symbolic link$leadsTo that cannot be followed (${ex.javaClass.simpleName}), so the engine writes no $writes in this run. Repair or remove the link, and deploy again.",
            ex,
        )
    }
    val realRoot = directory.toPath().toRealPath()
    val problem = when {
        !Files.isDirectory(real) -> "'$path' is not a directory, so the engine writes no $writes in this run. Remove what is at that path, and deploy again."
        !admits(real, realRoot) ->
            "'$path' leads to '$real' once links are resolved, outside the project directory '$realRoot', so the engine writes no $writes in this run. The engine writes only inside the project: replace the link with a directory, and deploy again."
        else -> return
    }
    throw ToolDirectoryException(problem)
}

/**
 * Returns the path this file has once the links of its nearest ancestor that exists are resolved, followed by the rest of the path as it is written, normalized, so two paths that reach one file through different links, such as a project directory and a link to it, give the same result. A link that leads nowhere counts as missing.
 */
internal fun File.resolvedPath(): File {
    val absolute = absoluteFile.toPath().normalize()
    val existing = generateSequence(absolute) { it.parent }.firstOrNull { Files.exists(it) } ?: return absolute.toFile()
    return try {
        existing
            .toRealPath()
            .resolve(existing.relativize(absolute))
            .normalize()
            .toFile()
    } catch (_: IOException) {
        // A directory that cannot be resolved, such as one the user may not search, is named as written; a comparison of it then only misses a link, as a lexical one did.
        absolute.toFile()
    }
}

/**
 * Returns where the symbolic link [link] leads, a relative target named as the path it stands for, or `null` when the link cannot be read.
 */
internal fun linkTarget(link: Path): Path? = try {
    link.resolveSibling(Files.readSymbolicLink(link)).normalize()
} catch (_: IOException) {
    null
}

/**
 * Thrown when a directory a tool writes its files into cannot hold them - see [requireToolDirectory]. It fails that tool of that deployment only, and the engine goes on with the next one.
 */
class ToolDirectoryException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
