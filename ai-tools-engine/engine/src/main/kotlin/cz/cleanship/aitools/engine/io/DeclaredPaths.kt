package cz.cleanship.aitools.engine.io

import java.io.File

/**
 * Resolves [path] as it was declared in a YAML file - `locations.*` in `config.yml`, `deploy.directory` in
 * `project.yml` - against this directory, which is the `--working-dir` of the run.
 *
 * Every relative path an author writes therefore shares one base, and none of them depends on the working
 * directory of the JVM process: that differs between launchers (`:cli:run` sets it to `ai-tools-engine/cli`,
 * `installDist` and `java -jar` leave it wherever the caller stood), which would make the same value point at
 * different directories. It matters most for `deploy.directory`, because the adapters delete under it.
 *
 * An absolute [path] is returned exactly as declared. A relative one is made absolute but is not normalized, so
 * `.` and `..` segments survive into the resolved path - they address the same file either way, and normalizing
 * would resolve them lexically, past any symbolic link on the way.
 */
fun File.resolveDeclaredPath(path: String): File {
    val declared = File(path)
    return if (declared.isAbsolute) declared else File(this, path).absoluteFile
}
