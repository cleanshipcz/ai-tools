package cz.cleanship.aitools.engine.io

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Deletes this artifact directory together with everything under it, after making sure it really is under [owned].
 *
 * A replacing deploy is the one moment the engine removes files it did not write in this run, and in the user scope
 * it does so inside a home the user shares with everything they installed themselves. Two things therefore bound it:
 *
 * - The directory has to be inside [owned]. The decision is made on the canonical paths, so a directory reached
 *   through a symbolic link is judged by where it really is rather than by how it was named.
 * - The walk below it never follows a symbolic link. A link is removed as a link, so a corpus linked into a skill
 *   bundle loses the link and keeps its contents - the target belongs to the user, not to this deploy.
 *
 * That a path is inside [owned] at all is what id validation guarantees: [cz.cleanship.aitools.engine.services.LoaderService]
 * rejects a manifest whose id is not a single path segment, before any artifact of any scope is written, which is
 * what protects the writes as well. This check is the second line behind it rather than the only one.
 *
 * @throws ArtifactPathException if this path is not inside [owned]. Nothing was deleted by this call; whatever the
 * run had already exported before it stays where it is.
 */
fun File.deleteArtifactDirectoryWithin(owned: File, describedBy: String) {
    checkArtifactDirectoryWithin(owned, describedBy)
    deleteTreeWithoutFollowingLinks(toPath())
}

/**
 * Makes sure this artifact directory really is under [owned], without touching it - the guard of
 * [deleteArtifactDirectoryWithin] on its own. A dry run applies it in place of the deletion, so that a deploy which
 * would be refused is refused by the dry run too.
 *
 * @throws ArtifactPathException if this path is not inside [owned]
 */
fun File.checkArtifactDirectoryWithin(owned: File, describedBy: String) {
    val ownedPath = owned.canonicalFile.toPath()
    val artifactPath = canonicalFile.toPath()
    if (artifactPath == ownedPath || !artifactPath.startsWith(ownedPath)) {
        throw ArtifactPathException(
            "Refusing to replace '$absolutePath' for '$describedBy': it is not inside '${owned.absolutePath}'. " +
                "Give the manifest an id that names a single directory.",
        )
    }
}

/**
 * Removes [root] and everything below it, treating a symbolic link as an entry to unlink rather than a directory to
 * descend into. [Files.walkFileTree] does not follow links unless asked to, which is what separates this from
 * `File.deleteRecursively`: that one asks [File.isDirectory], which resolves the link and walks the target.
 */
private fun deleteTreeWithoutFollowingLinks(root: Path) {
    if (!Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return
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
}

/**
 * Thrown when an artifact would be removed from a path outside the directory its deploy owns. It aborts the run
 * rather than being collected like an unresolvable reference: a path that escapes its own scope is not a broken
 * reference to fix in one manifest but a deploy about to touch something nobody asked it to.
 */
class ArtifactPathException(message: String) : RuntimeException(message)
