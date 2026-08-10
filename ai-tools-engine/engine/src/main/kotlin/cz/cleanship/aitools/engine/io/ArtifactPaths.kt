package cz.cleanship.aitools.engine.io

import java.io.File

/**
 * Deletes this artifact directory together with everything under it, after making sure it really is under [owned].
 *
 * A replacing deploy is the one moment the engine removes files it did not write in this run, and in the user scope
 * it does so inside a home the user shares with everything they installed themselves. The directory it deletes is
 * named after a manifest id, and nothing validates that id today, so an id carrying `..` segments would otherwise
 * walk the delete out of the directory the deploy owns.
 *
 * The containment is decided on the canonical paths, so a symbolic link pointing out of [owned] is caught as well
 * rather than only the `..` segments that are visible in the path.
 *
 * @throws ArtifactPathException if this path is not inside [owned], leaving everything on disk untouched
 */
fun File.deleteArtifactDirectoryWithin(owned: File, describedBy: String) {
    val ownedPath = owned.canonicalFile.toPath()
    val artifactPath = canonicalFile.toPath()
    if (artifactPath == ownedPath || !artifactPath.startsWith(ownedPath)) {
        throw ArtifactPathException(
            "Refusing to replace '$absolutePath' for '$describedBy': it is not inside '${owned.absolutePath}'. " +
                "Give the manifest an id that names a single directory.",
        )
    }
    deleteRecursively()
}

/**
 * Thrown when an artifact would be written to or removed from a path outside the directory its deploy owns, which
 * a manifest id carrying path segments is what produces. It aborts the run rather than being collected like an
 * unresolvable reference: an id that escapes its own scope is not a broken reference to fix in one manifest but a
 * deploy about to touch something nobody asked it to.
 */
class ArtifactPathException(message: String) : RuntimeException(message)
