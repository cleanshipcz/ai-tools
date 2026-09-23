package cz.cleanship.aitools.engine.io

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * How one path lies relative to another once both have their symbolic links resolved.
 */
enum class PathOverlap(
    /** Completes the sentence "the first path ... the second one", for a message naming both paths. */
    val description: String,
) {
    SAME("is"),
    INSIDE("lies inside"),
    CONTAINS("contains"),
}

/**
 * A symbolic link, together with the real path it leads to.
 *
 * @property path where the link is, named under the path it was found below
 * @property destination the real path the link leads to, with every link along it resolved; for a link that leads to nothing, the path it would lead to
 */
data class SymbolicLink(
    val path: File,
    val destination: Path,
)

/**
 * Returns how this path lies relative to [other], or `null` when neither is, contains, or lies inside the other.
 *
 * Both paths are compared by their real location on disk - see [realPathAllowingMissing] - so a link to a folder overlaps that folder, and a `..` that follows a link climbs out of the folder the link leads to.
 */
fun File.overlapWith(other: File): PathOverlap? =
    realPathAllowingMissing().overlapWithRealPath(other.realPathAllowingMissing())

/**
 * Returns how this real path lies relative to the real path [other], or `null` when neither is, contains, or lies inside the other.
 */
internal fun Path.overlapWithRealPath(other: Path): PathOverlap? = when {
    this == other -> PathOverlap.SAME
    startsWith(other) -> PathOverlap.INSIDE
    other.startsWith(this) -> PathOverlap.CONTAINS
    else -> null
}

/**
 * Returns where this path really points: its longest existing ancestor as the file system resolves it, every symbolic link and every `.` and `..` included, followed by the rest of the path with its `.` and `..` segments collapsed.
 */
internal fun File.realPathAllowingMissing(): Path {
    // Not normalized up front: collapsing 'link/..' as text would skip the link, while the file system climbs out of the folder the link leads to.
    val absolute = toPath().toAbsolutePath()
    // Walking up to the first ancestor that exists is what lets a target that a deploy has not created yet be compared: its existing parent may still be, or sit behind, a link.
    var existing: Path? = absolute
    while (existing != null && !Files.exists(existing)) {
        existing = existing.parent
    }
    if (existing == null) return absolute.normalize()
    return existing.toRealPath().resolve(existing.relativize(absolute)).normalize()
}

/**
 * What a walk below one path found, without following any symbolic link.
 *
 * @property links every symbolic link below the path
 * @property unreadableFolders every folder below the path, or the path itself, that the walk could not open, named under the path as given
 */
data class DirectoryScan(
    val links: List<SymbolicLink>,
    val unreadableFolders: List<File>,
)

/**
 * Walks everything below this path without following any symbolic link, and returns the links it found and the folders it could not open; both are empty when this path is not an existing directory.
 *
 * The walk starts from the real location of this path, and nothing below a folder it could not open is reported. Each [SymbolicLink.path] and each unreadable folder is named under this path as given, even when this path is reached through a link.
 */
internal fun File.scanBelow(): DirectoryScan {
    if (!isDirectory) return DirectoryScan(emptyList(), emptyList())
    val root = toPath().toRealPath()
    val links = mutableListOf<SymbolicLink>()
    val unreadableFolders = mutableListOf<File>()
    Files.walkFileTree(
        root,
        object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isSymbolicLink) {
                    links += SymbolicLink(resolve(root.relativize(file).toString()), file.linkDestination())
                }
                return FileVisitResult.CONTINUE
            }

            // Reported rather than skipped: deleting a directory the walk cannot open fails midway, after the entries visited before it are already gone, so a replacing deploy has to be refused before it starts.
            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                unreadableFolders += resolve(root.relativize(file).toString())
                return FileVisitResult.CONTINUE
            }
        },
    )
    return DirectoryScan(links, unreadableFolders)
}

/**
 * Returns the real path this symbolic link leads to, resolving a relative link against the folder holding it; for a link that leads to nothing, the path it would lead to.
 */
internal fun Path.linkDestination(): Path = parent.resolve(Files.readSymbolicLink(this)).toFile().realPathAllowingMissing()
