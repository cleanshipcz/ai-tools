package cz.cleanship.aitools.engine.tools.mcp

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.UserPrincipal

/**
 * Tells who owns a file and who may write it, and which user runs the engine, as the file system reports them.
 */
interface LauncherFileOwnership {

    /**
     * Returns the POSIX attributes of [path], following links: its owner and its permissions.
     *
     * @throws UnsupportedOperationException when the file system of [path] has no POSIX attributes, such as the default file system on Windows
     * @throws java.io.IOException when the attributes cannot be read
     */
    fun attributesOf(path: Path): PosixFileAttributes

    /**
     * Returns the user running the engine, as the file system names an owner.
     *
     * @throws java.io.IOException when the file system knows no user of that name, such as for a user id without an entry in the user database
     */
    fun runningUser(): UserPrincipal

    companion object {
        /** The default file system and the user of the system property `user.name`. */
        val SYSTEM: LauncherFileOwnership = object : LauncherFileOwnership {
            override fun attributesOf(
                path: Path,
            ): PosixFileAttributes = Files.readAttributes(path, PosixFileAttributes::class.java)

            override fun runningUser(): UserPrincipal = FileSystems.getDefault().userPrincipalLookupService.lookupPrincipalByName(System.getProperty("user.name"))
        }
    }
}
