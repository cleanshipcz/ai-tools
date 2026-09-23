package cz.cleanship.aitools.engine.io

import java.io.File
import java.io.IOException
import java.nio.file.FileSystemException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Deletes this artifact directory together with everything under it, after making sure it really is under [owned].
 *
 * A replacing deploy is the one moment the engine removes files it did not write in this run, and in the user scope it does so inside a home the user shares with everything they installed themselves. Two things therefore bound it:
 *
 * - The directory has to be inside [owned]. The decision is made on the canonical paths, so a directory reached through a symbolic link is judged by where it really is rather than by how it was named.
 * - The walk below it never follows a symbolic link - see [deleteTreeWithoutFollowingLinks].
 *
 * That a path is inside [owned] at all is what id validation guarantees: [cz.cleanship.aitools.engine.services.LoaderService] rejects a manifest whose id is not a single path segment, before any artifact of any scope is written, which is what protects the writes as well. This check is the second line behind it rather than the only one.
 *
 * @throws ArtifactPathException if this path is not inside [owned], judged by where it leads when it is a symbolic link. Nothing was deleted by this call; whatever the run had already exported before it stays where it is.
 * @throws ArtifactDeleteException if an entry below this path cannot be deleted
 */
fun File.deleteArtifactDirectoryWithin(owned: File, describedBy: String) {
    checkArtifactDirectoryWithin(owned, describedBy)
    deleteTreeWithoutFollowingLinks()
}

/**
 * Makes sure this artifact directory really is under [owned], without touching it - the guard of [deleteArtifactDirectoryWithin] on its own. A dry run applies it in place of the deletion, so that a deploy which would be refused is refused by the dry run too.
 *
 * @throws ArtifactPathException if this path is not inside [owned], judged by where it leads when it is a symbolic link
 */
fun File.checkArtifactDirectoryWithin(owned: File, describedBy: String) {
    val ownedPath = owned.canonicalFile.toPath()
    val artifactPath = canonicalFile.toPath()
    if (artifactPath == ownedPath || !artifactPath.startsWith(ownedPath)) {
        // A link is judged by where it leads, so it is refused even though the delete would only unlink it; renaming the id cannot help there, removing the link or the replace can.
        val message = if (Files.isSymbolicLink(toPath())) {
            "Refusing to replace '$absolutePath' for '$describedBy': it is a symbolic link that leads to '$artifactPath', which is not inside '${owned.absolutePath}'. " +
                "Remove the link, or turn off 'replace' for that deployment."
        } else {
            "Refusing to replace '$absolutePath' for '$describedBy': it is not inside '${owned.absolutePath}'. " +
                "Give the manifest an id that names a single directory."
        }
        throw ArtifactPathException(message)
    }
}

/**
 * Removes this path and everything below it, treating a symbolic link, including this path itself when it is one, as an entry to unlink rather than a directory to descend into, so what a link leads to is never touched.
 *
 * Nothing happens when this path does not exist.
 *
 * @throws ArtifactDeleteException if an entry cannot be deleted
 */
internal fun File.deleteTreeWithoutFollowingLinks() {
    val root = toPath()
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
    try {
        // Files.walkFileTree does not follow links unless asked to, which is what separates this from File.deleteRecursively: that one asks File.isDirectory, which resolves a link and walks its target.
        Files.walkFileTree(
            root,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) throw exc
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    } catch (ex: IOException) {
        // A dedicated type lets the engine add the deployment and the tool to exactly this failure, without also catching unrelated I/O failures of the export around it.
        throw ArtifactDeleteException(ex.failedEntry(root), ex)
    }
}

/**
 * Returns the entry this failure of deleting [root] names: the file of a [FileSystemException] that names one, otherwise [root] itself.
 */
// A failure naming no file, such as one closing the listing of a directory, still happened while deleting root, which is a real path a message can point at; its own text may be empty.
internal fun IOException.failedEntry(root: Path): String = (this as? FileSystemException)?.file ?: root.toString()

/**
 * Thrown when a replacing deploy cannot delete an entry of a directory it replaces, which leaves that directory partly deleted.
 *
 * @property entry the path that could not be deleted, or the directory being deleted when the failure names no path
 * @property cause the failure of the file system that stopped the delete
 */
// The message starts in lower case because it is quoted inside the sentence of the failure that reports it to the user.
class ArtifactDeleteException(
    val entry: String,
    override val cause: IOException,
) : IOException("deleting '$entry' failed (${cause.javaClass.simpleName})", cause)

/**
 * Thrown when a directory a replacing deploy would remove is not inside the directory its deploy owns, judged by where it leads when it is a symbolic link. It aborts the run rather than being collected like an unresolvable reference: a path that escapes its own scope is not a broken reference to fix in one manifest but a deploy about to touch something nobody asked it to.
 */
class ArtifactPathException(message: String) : RuntimeException(message)
