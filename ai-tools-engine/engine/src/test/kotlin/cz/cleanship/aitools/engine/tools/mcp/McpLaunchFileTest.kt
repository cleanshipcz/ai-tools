package cz.cleanship.aitools.engine.tools.mcp

import cz.cleanship.aitools.engine.launcher.McpLauncherContract
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlin.io.path.createDirectories
import kotlin.io.path.readLines
import kotlin.io.path.writeText

// The launcher script as a file: where the tests find it, how git records it, the block of refused names the engine reads, and how it starts under shells that are not tested line by line.
class McpLaunchFileTest {

    @TempDir
    lateinit var root: Path

    @Nested
    inner class Location {

        @Test
        fun `should be an executable file in the working tree`() {
            // then
            assertThat(mcpLaunchScript).isRegularFile()
            assertThat(Files.isExecutable(mcpLaunchScript)).isTrue()
        }

        @Test
        fun `should find the launcher in the nearest directory above the working directory when the test task does not name it`() {
            // given
            // - a checkout whose scripts directory lies two levels above the working directory, as ai-tools-engine/engine lies below the repository
            val launcher = root.resolve("checkout/scripts/mcp-launch").also { it.parent.createDirectories() }.also { it.writeText("#!/bin/sh\n") }
            val workingDirectory = root.resolve("checkout/ai-tools-engine/engine").createDirectories()

            // when
            val found = findLauncherAbove(workingDirectory)

            // then
            assertThat(found).isEqualTo(launcher)
        }

        @Test
        fun `should fail naming the system property when no directory above the working directory holds the launcher`() {
            // given
            val workingDirectory = root.resolve("elsewhere/engine").createDirectories()

            // when / then
            assertThatThrownBy { findLauncherAbove(workingDirectory) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("scripts/mcp-launch")
                .hasMessageContaining("aitools.mcpLaunch")
        }

        @Test
        fun `should be tracked by git with the executable mode 100755`() {
            // given
            // - a source archive or a container without git has no work tree to ask; the mode is then checked where it is recorded, in a checkout
            val repository = mcpLaunchScript.parent.parent
            assumeTrue(isGitWorkTree(repository)) { "$repository is not a git work tree, or git is not installed, so the recorded mode cannot be read" }

            // when
            val output =
                run(repository, "git", "ls-files", "--stage", "--", repository.relativize(mcpLaunchScript).toString())

            // then
            assertThat(output).startsWith("100755 ")
        }
    }

    @Nested
    inner class RefusedNames {

        @Test
        fun `should list the refused secret names in one block, one uppercase name or prefix or suffix pattern per line`() {
            // when
            val patterns = refusedSecretNamePatterns()

            // then
            // - each line states the class of its pattern first
            assertThat(refusedSecretNameClasses().map { it.first }.toSet()).containsExactlyInAnyOrder('A', 'B')
            assertThat(patterns).isNotEmpty().doesNotHaveDuplicates()
            assertThat(patterns).allSatisfy({ pattern -> assertThat(pattern).matches("\\*?[A-Z0-9_]+\\*?") })
            // - the engine refuses the same names at load time, from its one copy of the block
            assertThat(patterns).containsExactlyInAnyOrderElementsOf(McpLauncherContract.REFUSED_NAME_PATTERNS)
        }

        @Test
        fun `should fail when the launcher holds no block of refused names`() {
            // given
            val script = root.resolve("mcp-launch").also { it.writeText("#!/bin/sh -p\nexec \"\$@\"\n") }

            // when / then
            assertThatThrownBy { refusedSecretNamePatterns(script) }
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("mcp_launch_refused_names='")
        }
    }

    @Nested
    inner class Source {

        @Test
        fun `should hold no here-document or here-string, which bash would write to a file in TMPDIR`() {
            // given
            // - TMPDIR is of class B, so a manifest may set it for a server started through the launcher; it may never matter to the launcher's shell
            val code = mcpLaunchScript.readLines().filterNot { it.trimStart().startsWith("#") }

            // then
            assertThat(code).noneMatch { "<<" in it }
        }

        @Test
        fun `should start no program by a path of its own, only the helpers it finds on the PATH and the command`() {
            // given
            val code = mcpLaunchScript.readLines().filterNot { it.trimStart().startsWith("#") }

            // then
            assertThat(code).noneMatch { Regex("""(^|[\s;|&(`])/(usr/)?(local/)?s?bin/""").containsMatchIn(it) }
        }

        @Test
        fun `should never call eval`() {
            // given
            // - comments may name eval; the code must not
            val code = mcpLaunchScript.readLines().filterNot { it.trimStart().startsWith("#") }

            // then
            assertThat(code).noneMatch { Regex("\\beval\\b").containsMatchIn(it) }
        }
    }

    @Nested
    inner class Interpreter {

        @Test
        fun `should start the server through its own #! line under the sh of this machine`() {
            // given
            assumeTrue(McpLaunchSandbox.missingPrerequisite == null) { McpLaunchSandbox.missingPrerequisite }
            val sandbox = McpLaunchSandbox(root, shell = null)
            sandbox.storeInKeyring("JIRA_PAT", "keyring-value")

            // when
            val result = sandbox.launch(listOf("atlassian", "--required", "JIRA_PAT", "--", sandbox.probeServer.toString(), "argument"))

            // then
            assertThat(result.exitCode).describedAs(result.stderr).isZero()
            assertThat(result.server().arguments).containsExactly("argument")
            assertThat(result.server().environment).containsEntry("JIRA_PAT", "keyring-value")
        }

        @Test
        fun `should not start anything under BusyBox ash, which refuses the option -p of the #! line`() {
            // given
            // - without -p, bash as /bin/sh would read SHELLOPTS, BASHOPTS and functions from the environment before the first line
            assumeTrue(McpLaunchSandbox.missingPrerequisite == null) { McpLaunchSandbox.missingPrerequisite }
            assumeTrue(LaunchShell.BUSYBOX.location != null) { "busybox is not installed, so its ash cannot be tried as /bin/sh" }
            val sandbox = McpLaunchSandbox(root, LaunchShell.BUSYBOX)
            sandbox.storeInKeyring("JIRA_PAT", "keyring-value")

            // when
            val result = sandbox.launch(listOf("atlassian", "--required", "JIRA_PAT", "--", sandbox.probeServer.toString()))

            // then
            assertThat(McpLaunchSandbox.shebangOptions()).containsExactly("-p")
            assertThat(result.exitCode).isEqualTo(2)
            assertThat(result.stdout).isEmpty()
            assertThat(result.stderr).contains("illegal option -p")
            assertThat(sandbox.lookups()).isEmpty()
        }
    }

    private fun isGitWorkTree(directory: Path): Boolean = try {
        run(directory, "git", "rev-parse", "--is-inside-work-tree").trim() == "true"
    } catch (_: IOException) {
        false
    } catch (_: IllegalStateException) {
        false
    }

    private fun run(directory: Path, vararg command: String): String {
        val process = ProcessBuilder(*command).directory(directory.toFile()).redirectErrorStream(true).start()
        val output = process.inputStream.use { String(it.readBytes()) }
        check(process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS) && process.exitValue() == 0) { "${command.joinToString(" ")} failed: $output" }
        return output
    }

    companion object {
        private const val GIT_TIMEOUT_SECONDS = 30L
    }
}
