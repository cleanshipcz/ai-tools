package cz.cleanship.aitools.engine.services

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.env.EnvironmentSource
import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.SkillFile
import cz.cleanship.aitools.engine.models.SkillManifest
import cz.cleanship.aitools.engine.models.SkillSection
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assumptions.assumeFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class SkillSourceResolverTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var projectsFolder: File
    private lateinit var manifestFile: File
    private lateinit var resolver: SkillSourceResolver

    private val logAppender = ListAppender<ILoggingEvent>()
    private val resolverLogger = LoggerFactory.getLogger(SkillSourceResolver::class.java) as Logger

    @BeforeEach
    fun setUp() {
        logAppender.start()
        resolverLogger.addAppender(logAppender)
        projectsFolder = tempDir.resolve("projects").toFile()
        manifestFile = tempDir.resolve("ai-tools/04_skills/jira-ticket/skill.yml").toFile()
        manifestFile.parentFile.mkdirs()
        manifestFile.writeText("id: jira-ticket\n")
        // The environment is empty so that no variable of the machine running the test can leak into a resolution.
        resolver = SkillSourceResolver(
            VariableResolver(
                variables = mapOf("PROJECTS_FOLDER" to projectsFolder.absolutePath),
                environment = { null },
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        resolverLogger.detachAppender(logAppender)
        logAppender.stop()
        logAppender.list.clear()
    }

    @Nested
    inner class Resolution {

        @Test
        fun `should take the description, the body from its first line that is not empty, and the companion files from the source folder`() {
            // given
            // - a plain skill with nested and top-level companion files
            val sourceDir = writePlainSkill(
                projectsFolder.resolve("mcp/skills/jira-ticket"),
                frontmatter = "name: jira-ticket\ndescription: \"Create Jira tickets. Use when: filing a bug.\"\n",
                body = "\n# jira-ticket\n\nUse templates/task.txt.\n",
            )
            writeFile(sourceDir.resolve("templates/task.txt"), "task")
            writeFile(sourceDir.resolve("templates/nested/epic.txt"), "epic")
            writeFile(sourceDir.resolve("field-defaults.json"), "{}")

            // when
            val resolved = resolver.resolve(pointer("\${PROJECTS_FOLDER}/mcp/skills/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.directory.canonicalFile).isEqualTo(sourceDir.canonicalFile)
            assertThat(resolved.skill.description).isEqualTo("Create Jira tickets. Use when: filing a bug.")
            assertThat(resolved.skill.body).isEqualTo("# jira-ticket\n\nUse templates/task.txt.\n")
            assertThat(resolved.skill.files).containsExactly(
                SkillFile("field-defaults.json", "field-defaults.json"),
                SkillFile("templates/nested/epic.txt", "templates/nested/epic.txt"),
                SkillFile("templates/task.txt", "templates/task.txt"),
            )
        }

        @Test
        fun `should keep the id, source and metadata the manifest declares`() {
            // given
            writePlainSkill(projectsFolder.resolve("mcp/skills/jira-ticket"))
            val skill = pointer("\${PROJECTS_FOLDER}/mcp/skills/jira-ticket")

            // when
            val resolved = resolver.resolve(skill, manifestFile)

            // then
            assertThat(resolved.skill.id).isEqualTo(skill.id)
            assertThat(resolved.skill.source).isEqualTo(skill.source)
            assertThat(resolved.skill.metadata).isEqualTo(skill.metadata)
        }

        @Test
        fun `should resolve a relative source against the directory of the manifest file`() {
            // given
            // - the source sits two levels above the directory holding skill.yml
            val sourceDir = writePlainSkill(tempDir.resolve("ai-tools/plain/jira-ticket").toFile())

            // when
            val resolved = resolver.resolve(pointer("../../plain/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.directory.canonicalFile).isEqualTo(sourceDir.canonicalFile)
        }

        @Test
        fun `should never offer the SKILL md itself as a companion file`() {
            // given
            writePlainSkill(projectsFolder.resolve("jira-ticket"))

            // when
            val resolved = resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.skill.files).isEmpty()
        }

        @Test
        fun `should offer a symlinked file as a companion file`() {
            // given
            // - a companion file linked in from outside the source folder
            val sourceDir = writePlainSkill(projectsFolder.resolve("jira-ticket"))
            val outside = writeFile(tempDir.resolve("shared/defaults.json").toFile(), "{}")
            Files.createSymbolicLink(sourceDir.resolve("defaults.json").toPath(), outside.toPath())

            // when
            val resolved = resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.skill.files).containsExactly(SkillFile("defaults.json", "defaults.json"))
        }

        @Test
        fun `should read a description written as a folded block scalar`() {
            // given
            writePlainSkill(
                projectsFolder.resolve("jira-ticket"),
                frontmatter = "name: jira-ticket\ndescription: >-\n  First line\n  second line\n",
            )

            // when
            val resolved = resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.skill.description).isEqualTo("First line second line")
        }

        @Test
        fun `should read a SKILL md written with Windows line endings`() {
            // given
            val sourceDir = projectsFolder.resolve("jira-ticket")
            writeFile(
                sourceDir.resolve("SKILL.md"),
                "---\r\nname: jira-ticket\r\ndescription: A skill\r\n---\r\n\r\n# Body\r\n",
            )

            // when
            val resolved = resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.skill.description).isEqualTo("A skill")
            assertThat(resolved.skill.body).isEqualTo("# Body\r\n")
        }

        @Test
        fun `should keep a body that is empty`() {
            // given
            writePlainSkill(projectsFolder.resolve("jira-ticket"), body = "")

            // when
            val resolved = resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.skill.body).isEmpty()
        }

        @Test
        fun `should read a SKILL md that starts with a byte order mark and leave the mark out of the body`() {
            // given
            // - editors on Windows commonly save UTF-8 with a leading byte order mark
            val sourceDir = projectsFolder.resolve("jira-ticket")
            writeFile(sourceDir.resolve("SKILL.md"), "\uFEFF---\nname: jira-ticket\ndescription: A skill\n---\n\n# jira-ticket\n")

            // when
            val resolved = resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.skill.description).isEqualTo("A skill")
            assertThat(resolved.skill.body).isEqualTo("# jira-ticket\n")
        }

        @Test
        fun `should offer hidden files and the files of hidden folders as companion files`() {
            // given
            val sourceDir = writePlainSkill(projectsFolder.resolve("jira-ticket"))
            writeFile(sourceDir.resolve(".env.example"), "TOKEN=")
            writeFile(sourceDir.resolve(".config/defaults.json"), "{}")

            // when
            val resolved = resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.skill.files).containsExactly(
                SkillFile(".config/defaults.json", ".config/defaults.json"),
                SkillFile(".env.example", ".env.example"),
            )
        }

        @Test
        fun `should ignore frontmatter keys other than name and description and warn naming the skill and the keys`() {
            // given
            writePlainSkill(
                projectsFolder.resolve("jira-ticket"),
                frontmatter = "name: jira-ticket\ndescription: A skill\nallowed-tools: Bash\nlicense: MIT\n",
            )

            // when
            val resolved = resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.skill.description).isEqualTo("A skill")
            assertThat(resolved.skill.body).doesNotContain("allowed-tools")
            assertThat(warnings()).singleElement().satisfies({ warning ->
                assertThat(warning).contains("'jira-ticket'", "allowed-tools", "license")
            })
        }

        @Test
        fun `should warn about nothing when the frontmatter holds only name and description`() {
            // given
            writePlainSkill(projectsFolder.resolve("jira-ticket"))

            // when
            resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

            // then
            assertThat(warnings()).isEmpty()
        }
    }

    @Nested
    inner class Validation {

        @Test
        fun `should fail naming the declared and resolved path when the source folder does not exist`() {
            // given
            val missing = projectsFolder.resolve("missing")

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/missing"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining("'source'")
                .hasMessageContaining("\${PROJECTS_FOLDER}/missing")
                .hasMessageContaining(missing.absolutePath)
                .hasMessageContaining("does not exist")
        }

        @Test
        fun `should fail when the source names a file rather than a folder`() {
            // given
            val file = writeFile(projectsFolder.resolve("SKILL.md"), "---\nname: x\ndescription: y\n---\n")

            // when / then
            assertThatThrownBy { resolver.resolve(pointer(file.absolutePath), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining("is not a directory")
        }

        @Test
        fun `should fail naming the folder when it holds no SKILL md`() {
            // given
            val sourceDir = projectsFolder.resolve("jira-ticket")
            writeFile(sourceDir.resolve("README.md"), "Not a skill")

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(sourceDir.resolve("SKILL.md").absolutePath)
                .hasMessageContaining("does not exist")
        }

        @Test
        fun `should fail naming the SKILL md when it does not start with a frontmatter`() {
            // given
            val sourceDir = projectsFolder.resolve("jira-ticket")
            writeFile(sourceDir.resolve("SKILL.md"), "# jira-ticket\n\nNo frontmatter here.\n")

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(sourceDir.resolve("SKILL.md").absolutePath)
                .hasMessageContaining("frontmatter")
        }

        @Test
        fun `should fail naming the SKILL md when its frontmatter is never closed`() {
            // given
            val sourceDir = projectsFolder.resolve("jira-ticket")
            writeFile(sourceDir.resolve("SKILL.md"), "---\nname: jira-ticket\ndescription: A skill\n\n# Body\n")

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(sourceDir.resolve("SKILL.md").absolutePath)
                .hasMessageContaining("frontmatter")
        }

        @Test
        fun `should fail naming the SKILL md when its frontmatter is not valid YAML`() {
            // given
            val sourceDir = writePlainSkill(
                projectsFolder.resolve("jira-ticket"),
                frontmatter = "name: [unclosed\ndescription: A skill\n",
            )

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(sourceDir.resolve("SKILL.md").absolutePath)
        }

        @Test
        fun `should fail naming the SKILL md when its frontmatter is not a mapping`() {
            // given
            val sourceDir = writePlainSkill(projectsFolder.resolve("jira-ticket"), frontmatter = "- a list\n")

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(sourceDir.resolve("SKILL.md").absolutePath)
                .hasMessageContaining("mapping")
        }

        @ParameterizedTest
        @CsvSource(
            "'description: A skill\n', name",
            "'name: jira-ticket\n', description",
            "'name: jira-ticket\ndescription: \"\"\n', description",
            "'name: jira-ticket\ndescription: [a, b]\n', description",
            "'', name",
        )
        fun `should fail naming the SKILL md and the field when the frontmatter lacks it`(
            frontmatter: String,
            field: String,
        ) {
            // given
            // - CsvSource hands the escapes over literally, so they are turned into line breaks here
            val sourceDir =
                writePlainSkill(projectsFolder.resolve("jira-ticket"), frontmatter = frontmatter.replace("\\n", "\n"))

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(sourceDir.resolve("SKILL.md").absolutePath)
                .hasMessageContaining("'$field'")
        }

        @Test
        fun `should fail naming both names when the frontmatter name differs from the manifest id`() {
            // given
            val sourceDir = writePlainSkill(
                projectsFolder.resolve("jira-ticket"),
                frontmatter = "name: jira-tickets\ndescription: A skill\n",
            )

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(sourceDir.resolve("SKILL.md").absolutePath)
                .hasMessageContaining("'jira-tickets'")
                .hasMessageContaining("'jira-ticket'")
        }

        @ParameterizedTest
        @CsvSource("description", "sections", "files", "'description, sections, files'")
        fun `should fail naming the fields a manifest declares together with a source`(fields: String) {
            // given
            // - the source is valid, so the only fault is the manifest declaring content of its own
            writePlainSkill(projectsFolder.resolve("jira-ticket"))
            val declared = fields.split(", ")
            val skill = pointer("\${PROJECTS_FOLDER}/jira-ticket").copy(
                description = if ("description" in declared) "A description" else "",
                sections = if ("sections" in declared) listOf(SkillSection.TextSection("Text")) else emptyList(),
                files = if ("files" in declared) listOf(SkillFile("a.txt", "a.txt")) else emptyList(),
            )

            // when / then
            assertThatThrownBy { resolver.resolve(skill, manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining("'source'")
                .satisfies({ error -> declared.forEach { assertThat(error).hasMessageContaining("'$it'") } })
        }

        @Test
        fun `should fail naming the folder when a link inside it loops back to it`() {
            // given
            // - a folder linked into itself, which a walk following links would otherwise never leave
            val sourceDir = writePlainSkill(projectsFolder.resolve("jira-ticket"))
            Files.createSymbolicLink(sourceDir.resolve("loop").toPath(), sourceDir.toPath())

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(sourceDir.absolutePath)
                .hasMessageContaining("cannot be read")
        }

        @Test
        fun `should fail naming the link when a link inside the source folder points at nothing`() {
            // given
            // - a companion file linked into a checkout that has since moved away
            val sourceDir = writePlainSkill(projectsFolder.resolve("jira-ticket"))
            val link = sourceDir.resolve("templates/task.txt")
            link.parentFile.mkdirs()
            Files.createSymbolicLink(link.toPath(), tempDir.resolve("moved-away/task.txt"))

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(link.absolutePath)
                .hasMessageContaining("points at nothing")
        }

        @Test
        fun `should fail naming the SKILL md when it cannot be read`() {
            // given
            val sourceDir = writePlainSkill(projectsFolder.resolve("jira-ticket"))
            val skillFile = sourceDir.resolve("SKILL.md")
            skillFile.setReadable(false)
            // - a superuser reads the file regardless of its permissions, so the failure cannot be provoked there
            assumeFalse(skillFile.canRead())

            // when / then
            assertThatThrownBy { resolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining(skillFile.absolutePath)
                .hasMessageContaining("cannot be read")
        }

        @Test
        fun `should fail naming the variable when the source references one nothing declares`() {
            // given
            val skill = pointer("\${UNDECLARED_FOLDER}/jira-ticket")

            // when / then
            assertThatThrownBy { resolver.resolve(skill, manifestFile) }
                .isInstanceOf(InvalidSkillSourceException::class.java)
                .hasMessageContaining("'source'")
                .hasMessageContaining("UNDECLARED_FOLDER")
        }

        @Test
        fun `should fail when the skill declares no source`() {
            // given
            val skill = pointer("ignored").copy(source = null)

            // when / then
            assertThatThrownBy { resolver.resolve(skill, manifestFile) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Test
    fun `should fall back to the environment for a variable the config does not declare`() {
        // given
        // - the variable comes from the environment of the run only
        val sourceDir = writePlainSkill(projectsFolder.resolve("jira-ticket"))
        val environmentResolver = SkillSourceResolver(
            VariableResolver(
                environment = EnvironmentSource { name -> projectsFolder.absolutePath.takeIf { name == "PROJECTS_FOLDER" } },
            ),
        )

        // when
        val resolved = environmentResolver.resolve(pointer("\${PROJECTS_FOLDER}/jira-ticket"), manifestFile)

        // then
        assertThat(resolved.directory.canonicalFile).isEqualTo(sourceDir.canonicalFile)
    }

    @Nested
    inner class HomeDirectory {

        private lateinit var userHome: File

        @BeforeEach
        fun setUp() {
            userHome = tempDir.resolve("home").toFile()
        }

        @Test
        fun `should resolve a source starting with a tilde against the home directory`() {
            // given
            val sourceDir = writePlainSkill(userHome.resolve("mcp/skills/jira-ticket"))
            val homeResolver = SkillSourceResolver(VariableResolver(environment = { null }), userHome)

            // when
            val resolved = homeResolver.resolve(pointer("~/mcp/skills/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.directory.canonicalFile).isEqualTo(sourceDir.canonicalFile)
        }

        @Test
        fun `should resolve a tilde that a variable supplies against the home directory`() {
            // given
            // - the committed config.yml declares PROJECTS_FOLDER as a path under the home directory
            val sourceDir = writePlainSkill(userHome.resolve("Documents/Projects/mcp/skills/jira-ticket"))
            val homeResolver = SkillSourceResolver(
                VariableResolver(variables = mapOf("PROJECTS_FOLDER" to "~/Documents/Projects"), environment = { null }),
                userHome,
            )

            // when
            val resolved = homeResolver.resolve(pointer("\${PROJECTS_FOLDER}/mcp/skills/jira-ticket"), manifestFile)

            // then
            assertThat(resolved.directory.canonicalFile).isEqualTo(sourceDir.canonicalFile)
        }
    }

    private fun warnings() = logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }

    private fun pointer(source: String) = SkillManifest(
        id = "jira-ticket",
        source = source,
        metadata = ManifestMetadata(version = Version("2.0.0"), tags = setOf("jira")),
    )

    private fun writePlainSkill(
        directory: File,
        frontmatter: String = "name: jira-ticket\ndescription: A skill\n",
        body: String = "\n# jira-ticket\n",
    ): File {
        writeFile(directory.resolve("SKILL.md"), "---\n$frontmatter---\n$body")
        return directory
    }

    private fun writeFile(file: File, content: String): File {
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }
}
