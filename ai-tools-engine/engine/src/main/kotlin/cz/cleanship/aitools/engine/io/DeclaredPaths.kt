package cz.cleanship.aitools.engine.io

import java.io.File

/**
 * Resolves [path] as an author declared it - `locations.*` in `config.yml` or `config.local.yml`, `deploy.directory` in `project.yml`, `source` in a skill manifest, or the `--user-home` option of the command line - against this directory.
 *
 * Every relative path an author writes therefore shares one base, and none of them depends on the working directory of the JVM process: that differs between launchers (`:cli:run` sets it to `ai-tools-engine/cli`, `installDist` and `java -jar` leave it wherever the caller stood), which would make the same value point at different directories. It matters most for `deploy.directory`, because the adapters delete under it.
 *
 * A [path] that is exactly `~`, or starts with `~/`, resolves against [home] instead. Any other `~` is kept as written: `~user` is not expanded but read as a relative path whose first folder is named `~user`, and a `~` after the first character is an ordinary part of the name.
 *
 * An absolute [path] is returned exactly as declared. A relative one is made absolute but is not normalized, so `.` and `..` segments survive into the resolved path - they address the same file either way, and normalizing would resolve them lexically, past any symbolic link on the way.
 *
 * @param home the directory a leading `~` stands for; by default, the home directory of the user running the engine
 */
fun File.resolveDeclaredPath(path: String, home: File = File(System.getProperty("user.home"))): File {
    val underHome = path.removeHomePrefix()
    if (underHome != null) {
        return if (underHome.isEmpty()) home.absoluteFile else File(home, underHome).absoluteFile
    }
    val declared = File(path)
    return if (declared.isAbsolute) declared else File(this, path).absoluteFile
}

/**
 * Returns the part of this path after a leading `~` and its separator, an empty string for `~` alone, or `null` when this path does not start with a reference to the home directory.
 */
private fun String.removeHomePrefix(): String? = when {
    this == HOME -> ""
    startsWith("$HOME/") -> substring(HOME.length + 1)
    // A declared path is written with '/' on every platform, but a Windows author may still type the native separator.
    File.separatorChar != '/' && startsWith("$HOME${File.separatorChar}") -> substring(HOME.length + 1)
    else -> null
}

private const val HOME = "~"
