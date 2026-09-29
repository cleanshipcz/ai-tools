package cz.cleanship.aitools.engine.tools.mcp

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.launcher.McpLauncherContract
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.UserPrincipal
import java.nio.file.attribute.UserPrincipalNotFoundException
import java.time.Duration
import kotlin.io.path.createDirectories

/**
 * The libsecret secrets manager: it renders a stdio server as a start of the launcher `scripts/mcp-launch`, and tells whether the keyring holds a secret through the fake `busctl` of [FakeSecretService].
 */
class LibsecretSecretsManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var programs: FakePrograms
    private lateinit var secretService: FakeSecretService
    private lateinit var launcher: Path

    private val logAppender = ListAppender<ILoggingEvent>()
    private val managerLogger = LoggerFactory.getLogger(LibsecretSecretsManager::class.java) as Logger

    // - a server with a required and an optional secret, a secret read from the environment only and a plain value, in the order the resolver renders them
    private val transport = ResolvedMcpTransport.Stdio(
        command = "/opt/jira server/bin/jira-mcp-server",
        args = listOf("--name=it's \"quoted\"", "a b", "--"),
        env = linkedMapOf(
            "LOG_LEVEL" to McpValue.Plain("info"),
            "JIRA_PAT" to McpValue.Secret("JIRA_PAT", required = true),
            "CONFLUENCE_PAT" to McpValue.Secret("CONFLUENCE_PAT", required = false),
            "PROXY_TOKEN" to McpValue.Secret("PROXY_TOKEN", required = true),
        ),
    )
    private val managed =
        listOf(McpValue.Secret("JIRA_PAT", required = true), McpValue.Secret("CONFLUENCE_PAT", required = false))

    @BeforeEach
    fun setUp() {
        assumeTrue(missingProgramsReason() == null) { missingProgramsReason() }
        programs = FakePrograms(tempDir)
        secretService = FakeSecretService(tempDir, programs)
        launcher = FakePrograms.executable(tempDir.resolve("ai tools/scripts/mcp-launch"), "#!/bin/sh\nexec \"\$@\"\n")
        logAppender.start()
        managerLogger.addAppender(logAppender)
    }

    @AfterEach
    fun tearDown() {
        managerLogger.detachAppender(logAppender)
        logAppender.stop()
        logAppender.list.clear()
    }

    @Nested
    inner class Launch {

        @Test
        fun `should start the launcher by its absolute path with the id, each secret the manager supplies, the separator and the real command unchanged`() {
            // when
            val launched = manager().launch("atlassian", transport, managed)

            // then
            assertThat(launched.command).isEqualTo(launcher.toRealPath().toString())
            assertThat(launched.args).containsExactly(
                "atlassian",
                "--required",
                "JIRA_PAT",
                "--optional",
                "CONFLUENCE_PAT",
                "--",
                "/opt/jira server/bin/jira-mcp-server",
                "--name=it's \"quoted\"",
                "a b",
                "--",
            )
        }

        @Test
        fun `should keep every reference of the entry and make the references of the secrets it supplies optional, since the launcher enforces what is required`() {
            // when
            val launched = manager().launch("atlassian", transport, managed)

            // then
            // - a secret read from the environment only keeps its reference as it was; the launcher is not told about it
            assertThat(launched.env).containsExactly(
                org.assertj.core.api.Assertions
                    .entry("LOG_LEVEL", McpValue.Plain("info")),
                org.assertj.core.api.Assertions
                    .entry("JIRA_PAT", McpValue.Secret("JIRA_PAT", required = false)),
                org.assertj.core.api.Assertions
                    .entry("CONFLUENCE_PAT", McpValue.Secret("CONFLUENCE_PAT", required = false)),
                org.assertj.core.api.Assertions
                    .entry("PROXY_TOKEN", McpValue.Secret("PROXY_TOKEN", required = true)),
            )
            assertThat(launched.args).doesNotContain("PROXY_TOKEN")
        }

        @Test
        fun `should name the variables the launcher needs to reach the keyring, for a tool that clears the environment`() {
            // when
            val launched = manager().launch("atlassian", transport.copy(forwarded = listOf("XDG_RUNTIME_DIR", "EXTRA")), managed)

            // then
            assertThat(launched.forwarded).containsExactly("XDG_RUNTIME_DIR", "EXTRA", "DBUS_SESSION_BUS_ADDRESS")
        }

        @Test
        fun `should render the real path of a launcher reached through a symbolic link`() {
            // given
            val link = tempDir.resolve("checkout link")
            Files.createSymbolicLink(link, launcher.parent.parent)

            // when
            val launched = LibsecretSecretsManager(link.toFile(), environment()).launch("atlassian", transport, managed)

            // then
            assertThat(launched.command).isEqualTo(launcher.toRealPath().toString()).doesNotContain("checkout link")
        }

        @Test
        fun `should fail naming the server and the launcher when the launcher does not exist`() {
            // given
            val missing = tempDir.resolve("elsewhere/scripts/mcp-launch").toFile()

            // when / then
            assertThatThrownBy { LibsecretSecretsManager(tempDir.resolve("elsewhere").toFile(), environment()).launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessage(
                    "MCP server 'atlassian' reads its secrets through the launcher '${missing.absolutePath}', which does not exist. " +
                        "Restore it in the ai-tools repository, or set 'secrets_manager: environment' in config.local.yml to read every secret from the environment of the tool.",
                )
        }

        @ParameterizedTest
        @CsvSource(
            "directory, is not a regular file",
            "not-executable, is not executable",
        )
        fun `should fail naming the server and the launcher when the launcher cannot be started`(
            kind: String,
            problem: String,
        ) {
            // given
            val broken = tempDir.resolve("broken/scripts/mcp-launch")
            when (kind) {
                "directory" -> broken.createDirectories()
                else -> {
                    broken.parent.createDirectories()
                    Files.writeString(broken, "#!/bin/sh\n")
                }
            }

            // when / then
            assertThatThrownBy { LibsecretSecretsManager(tempDir.resolve("broken").toFile(), environment()).launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageStartingWith("MCP server 'atlassian' reads its secrets through the launcher '${broken.toFile().absolutePath}', which $problem.")
        }

        @Test
        fun `should fail when the path of the launcher holds a reference a tool would expand`() {
            // given
            val expanding = FakePrograms.executable(tempDir.resolve("\${HOME}/scripts/mcp-launch"), "#!/bin/sh\n")

            // when / then
            assertThatThrownBy { LibsecretSecretsManager(tempDir.resolve("\${HOME}").toFile(), environment()).launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("'atlassian'")
                .hasMessageContaining(expanding.toRealPath().toString())
                .hasMessageContaining("'\${'")
        }

        @Test
        fun `should refuse a server the loader did not check, whose id or secret name the launcher would refuse`() {
            // given
            // - the loader refuses both; the guard keeps a server built any other way from rendering a launcher that exits at once
            val manager = manager()

            // when / then
            assertThatThrownBy { manager.launch("jira server", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("'jira server'")
            assertThatThrownBy { manager.launch("atlassian", transport, listOf(McpValue.Secret("DISPLAY", required = true))) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("'DISPLAY'")
        }

        @Test
        fun `should name the pattern the launcher refuses a secret name by, for a server the loader did not check`() {
            // when / then
            assertThatThrownBy { manager().launch("atlassian", transport, listOf(McpValue.Secret("ld_preload", required = true))) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessage(
                    "MCP server 'atlassian' cannot pass the secret variable 'ld_preload' to the launcher, which refuses every secret name matching 'LD_*' without regard to case: " +
                        "${McpLauncherContract.REFUSAL_REASON}.",
                )
        }
    }

    /**
     * The launcher file a tool is given to start: a regular file of the checkout, owned by the user running the engine, executable by that user, and writable by no one else.
     */
    @Nested
    inner class LauncherFile {

        @Test
        fun `should render a launcher that is a link to a file inside the checkout by the real path of that file`() {
            // given
            val checkout = tempDir.resolve("linked")
            val real = FakePrograms.executable(checkout.resolve("bin/mcp-launch"), "#!/bin/sh\n")
            checkout.resolve("scripts").createDirectories()
            Files.createSymbolicLink(checkout.resolve("scripts/mcp-launch"), Path.of("../bin/mcp-launch"))

            // when
            val launched = LibsecretSecretsManager(checkout.toFile(), environment()).launch("atlassian", transport, managed)

            // then
            assertThat(launched.command).isEqualTo(real.toRealPath().toString())
        }

        @Test
        fun `should fail naming the launcher and the checkout when the launcher is a link to a file outside the checkout`() {
            // given
            val checkout = tempDir.resolve("checkout")
            val outside = FakePrograms.executable(tempDir.resolve("outside/mcp-launch"), "#!/bin/sh\n")
            checkout.resolve("scripts").createDirectories()
            Files.createSymbolicLink(checkout.resolve("scripts/mcp-launch"), outside)

            // when / then
            assertThatThrownBy { LibsecretSecretsManager(checkout.toFile(), environment()).launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessage(
                    "MCP server 'atlassian' reads its secrets through the launcher '${checkout.resolve("scripts/mcp-launch").toFile().absolutePath}', which resolves to '${outside.toRealPath()}', " +
                        "outside the ai-tools repository '${checkout.toRealPath()}'. Restore scripts/mcp-launch of the repository, " + REMEDY,
                )
        }

        @ParameterizedTest
        @CsvSource(
            "rwxrwxr-x, its group",
            "rwxr-xrwx, others",
            "rwxrwxrwx, its group and others",
        )
        fun `should fail naming the launcher when anyone but its owner may write it`(
            permissions: String,
            writers: String,
        ) {
            // given
            Files.setPosixFilePermissions(launcher, PosixFilePermissions.fromString(permissions))

            // when / then
            assertThatThrownBy { manager().launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessage(
                    "MCP server 'atlassian' reads its secrets through the launcher '${launcher.toRealPath()}', which $writers can write, so the file every tool starts could be changed by someone else. " +
                        "Remove that permission with: chmod go-w '${launcher.toRealPath()}', " + REMEDY,
                )
        }

        @ParameterizedTest
        @CsvSource(
            // - the directory of the launcher, and the root of the checkout, each writable by someone else
            "scripts, rwxrwxr-x, its group",
            "scripts, rwxr-xrwx, others",
            "checkout, rwxrwxrwx, its group and others",
        )
        fun `should fail naming the directory when anyone but its owner may write a directory from the launcher up to the root of the checkout`(
            directory: String,
            permissions: String,
            writers: String,
        ) {
            // given
            // - whoever may write such a directory may rename another file over the launcher without touching the launcher's own mode
            val writable = if (directory == "scripts") launcher.parent else launcher.parent.parent
            Files.setPosixFilePermissions(writable, PosixFilePermissions.fromString(permissions))

            // when / then
            assertThatThrownBy { manager().launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessage(
                    "MCP server 'atlassian' reads its secrets through the launcher '${launcher.toRealPath()}', whose directory '${writable.toRealPath()}' $writers can write, so the file every tool starts could be replaced by someone else. " +
                        "Remove that permission with: chmod go-w '${writable.toRealPath()}', " + REMEDY,
                )
        }

        @Test
        fun `should check the directories of the file a linked launcher resolves to, up to the root of the checkout`() {
            // given
            // - scripts/mcp-launch links to bin/mcp-launch of the same checkout, whose directory bin its group may write
            val checkout = tempDir.resolve("linked")
            val real = FakePrograms.executable(checkout.resolve("bin/mcp-launch"), "#!/bin/sh\n")
            checkout.resolve("scripts").createDirectories()
            Files.createSymbolicLink(checkout.resolve("scripts/mcp-launch"), Path.of("../bin/mcp-launch"))
            Files.setPosixFilePermissions(real.parent, PosixFilePermissions.fromString("rwxrwxr-x"))

            // when / then
            assertThatThrownBy { LibsecretSecretsManager(checkout.toFile(), environment()).launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessageContaining("whose directory '${real.parent.toRealPath()}' its group can write")
        }

        @Test
        fun `should accept a launcher whose checkout lies in a directory others may write, which is outside the checkout`() {
            // given
            // - a person who may write above the checkout could replace the whole checkout; that is outside what the check protects
            Files.setPosixFilePermissions(launcher.parent.parent.parent, PosixFilePermissions.fromString("rwxrwxrwx"))

            // when
            val launched = manager().launch("atlassian", transport, managed)

            // then
            assertThat(launched.command).isEqualTo(launcher.toRealPath().toString())
        }

        @Test
        fun `should fail naming the directory, its owner and the user running the engine when another user owns a directory of the checkout`() {
            // given
            // - the engine is told that the launcher belongs to the user running it, and that its directory belongs to someone else
            val someoneElse = UserPrincipal { "someone-else" }
            val user = LauncherFileOwnership.SYSTEM.runningUser()
            val directory = launcher.parent.toRealPath()
            val ownership = object : LauncherFileOwnership {
                override fun attributesOf(path: Path): PosixFileAttributes {
                    val attributes = LauncherFileOwnership.SYSTEM.attributesOf(path)
                    return if (path == directory) OwnedBy(someoneElse, attributes) else attributes
                }

                override fun runningUser(): UserPrincipal = user
            }

            // when / then
            assertThatThrownBy { manager(ownership = ownership).launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessage(
                    "MCP server 'atlassian' reads its secrets through the launcher '${launcher.toRealPath()}', whose directory '$directory' is owned by 'someone-else', not by '${user.name}', who runs the engine. " +
                        "Make '${user.name}' its owner, " + REMEDY,
                )
        }

        @Test
        fun `should fail naming the directory when the file system does not report who owns a directory of the checkout`() {
            // given
            // - the launcher itself reports an owner and permissions, its directory does not
            val directory = launcher.parent.toRealPath()
            val ownership = object : LauncherFileOwnership {
                override fun attributesOf(path: Path): PosixFileAttributes =
                    if (path == directory) throw UnsupportedOperationException("no posix view") else LauncherFileOwnership.SYSTEM.attributesOf(path)

                override fun runningUser(): UserPrincipal = LauncherFileOwnership.SYSTEM.runningUser()
            }

            // when / then
            assertThatThrownBy { manager(ownership = ownership).launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessage(
                    "MCP server 'atlassian' reads its secrets through the launcher '${launcher.toRealPath()}', but the engine cannot tell who owns its directory '$directory' and who may write it " +
                        "(UnsupportedOperationException), so it does not give it to any tool. Keep the ai-tools repository on a file system with POSIX owners and permissions, " + REMEDY,
                )
        }

        @Test
        fun `should fail naming the launcher, its owner and the user running the engine when another user owns the launcher`() {
            // given
            // - the engine is told it runs as another user than the one owning every file of this test
            val someoneElse = UserPrincipal { "someone-else" }
            val owner = Files.getOwner(launcher).name
            val ownership = object : LauncherFileOwnership {
                override fun attributesOf(
                    path: Path,
                ): PosixFileAttributes = LauncherFileOwnership.SYSTEM.attributesOf(path)

                override fun runningUser(): UserPrincipal = someoneElse
            }

            // when / then
            assertThatThrownBy { manager(ownership = ownership).launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessage(
                    "MCP server 'atlassian' reads its secrets through the launcher '${launcher.toRealPath()}', which is owned by '$owner', not by 'someone-else', who runs the engine. " +
                        "Make 'someone-else' its owner, " + REMEDY,
                )
        }

        @ParameterizedTest
        @CsvSource("attributes", "running-user")
        fun `should fail naming the launcher when the file system does not report who owns it and who may write it`(
            unknown: String,
        ) {
            // given
            // - a file system without POSIX attributes, such as the default one on Windows, has no owner and permission bits to read
            val ownership = object : LauncherFileOwnership {
                override fun attributesOf(path: Path): PosixFileAttributes =
                    if (unknown == "attributes") throw UnsupportedOperationException("no posix view") else LauncherFileOwnership.SYSTEM.attributesOf(path)

                override fun runningUser(): UserPrincipal = if (unknown == "running-user") throw UserPrincipalNotFoundException("?") else LauncherFileOwnership.SYSTEM.runningUser()
            }

            // when / then
            assertThatThrownBy { manager(ownership = ownership).launch("atlassian", transport, managed) }
                .isInstanceOf(McpServerResolvingException::class.java)
                .hasMessage(
                    "MCP server 'atlassian' reads its secrets through the launcher '${launcher.toRealPath()}', but the engine cannot tell who owns it and who may write it " +
                        "(${if (unknown == "attributes") "UnsupportedOperationException" else "UserPrincipalNotFoundException"}), so it does not give it to any tool. " +
                        "Keep the ai-tools repository on a file system with POSIX owners and permissions, " + REMEDY,
                )
        }

        @Test
        fun `should report the owner and the user running the engine the way the file system of this machine does`() {
            // when
            val user = LauncherFileOwnership.SYSTEM.runningUser()
            val owner = LauncherFileOwnership.SYSTEM.attributesOf(launcher).owner()

            // then
            // - every file of this test was created by the user running it
            assertThat(owner).isEqualTo(user)
        }
    }

    @Nested
    inner class Presence {

        @Test
        fun `should ask the keyring once per secret during the run, however many servers read it`() {
            // given
            secretService.store("JIRA_PAT")
            val manager = manager()

            // when
            val first = manager.presenceOf("JIRA_PAT")
            val again = manager.presenceOf("JIRA_PAT")
            val other = manager.presenceOf("CONFLUENCE_PAT")

            // then
            assertThat(first).isEqualTo(SecretPresence.Stored)
            assertThat(again).isEqualTo(SecretPresence.Stored)
            assertThat(other).isEqualTo(SecretPresence.Absent)
            assertThat(secretService.calls().map { it.arguments.last() }).containsExactly("JIRA_PAT", "CONFLUENCE_PAT")
        }

        @Test
        fun `should warn once during the run when secret-tool is not on the PATH`() {
            // given
            // - the fakes hold busctl but no secret-tool
            val manager = manager()

            // when
            manager.presenceOf("JIRA_PAT")
            manager.presenceOf("CONFLUENCE_PAT")
            manager.launch("atlassian", transport, managed)

            // then
            assertThat(warnings()).containsExactly(
                "The secrets manager of this machine is libsecret, but secret-tool is not on the PATH of this run. " +
                    "The launcher reads every secret from the environment of the tool unless the PATH of the tool holds secret-tool. " +
                    "Install secret-tool (package libsecret-tools), or set 'secrets_manager: environment' in config.local.yml.",
            )
        }

        @Test
        fun `should not warn when secret-tool is on the PATH`() {
            // given
            programs.install("mcp-launch/secret-tool")

            // when
            manager().presenceOf("JIRA_PAT")

            // then
            assertThat(warnings()).isEmpty()
        }

        @ParameterizedTest
        @CsvSource(
            delimiter = '|',
            value = [
                "'' | false",
                "\${JIRA_PAT} | false",
                "\${JIRA_PAT:-} | false",
                "\${env:JIRA_PAT} | false",
                "\${OTHER} | true",
                "token | true",
            ],
        )
        fun `should count an environment value as set exactly when the launcher does`(value: String, set: Boolean) {
            // when
            val counted = manager().countsAsSet("JIRA_PAT", value)

            // then
            assertThat(counted).isEqualTo(set).isEqualTo(McpLauncherContract.countsAsSet("JIRA_PAT", value))
        }

        @Test
        fun `should tell how to store a secret without its value on the command line`() {
            // when
            val command = manager().storeCommand("JIRA_PAT")

            // then
            assertThat(command).isEqualTo("secret-tool store --label='ai-tools MCP JIRA_PAT' service ai-tools-mcp variable JIRA_PAT")
        }
    }

    // The attributes of a real file with another owner.
    private class OwnedBy(
        private val owner: UserPrincipal,
        private val attributes: PosixFileAttributes,
    ) : PosixFileAttributes by attributes {
        override fun owner(): UserPrincipal = owner
    }

    private fun environment(): EnvironmentSource {
        val environment =
            mapOf("PATH" to programs.path, "DBUS_SESSION_BUS_ADDRESS" to "unix:path=/nonexistent/aitools-test-bus")
        return EnvironmentSource { environment[it] }
    }

    private fun manager(ownership: LauncherFileOwnership = LauncherFileOwnership.SYSTEM) =
        LibsecretSecretsManager(launcher.parent.parent.toFile(), environment(), probe = SecretServiceProbe(environment(), ANSWER_TIME_LIMIT), ownership = ownership)

    private fun warnings() = logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    companion object {
        // Every call of the fake busctl answers at once; the limit only has to be far above what a loaded machine needs to start it.
        private val ANSWER_TIME_LIMIT = Duration.ofSeconds(30)

        private const val REMEDY = "or set 'secrets_manager: environment' in config.local.yml to read every secret from the environment of the tool."
    }
}
