package cz.cleanship.aitools.engine.services

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import cz.cleanship.aitools.engine.data.ruleset
import cz.cleanship.aitools.engine.io.ArtifactPathException
import cz.cleanship.aitools.engine.models.SkillFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path

class ExportServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var exportService: ExportService
    private lateinit var targetFile: File

    @BeforeEach
    fun setUp() {
        exportService = ExportService()
        targetFile = tempDir.resolve("nested").resolve("ruleset.md").toFile()
    }

    /**
     * A companion file may legitimately come from outside the skill's own directory - an absolute `source` is a
     * documented shape - but in the user scope it lands in `~/.claude/skills/<id>/`, a directory whose purpose is to
     * be read into an agent's context. Such a copy is therefore named in the transcript rather than made quietly.
     */
    @Nested
    inner class SourcesFromOutsideTheSkill {

        private val logAppender = ListAppender<ILoggingEvent>()
        private lateinit var exportLogger: Logger
        private lateinit var sourceDir: File
        private lateinit var skillDir: File

        @BeforeEach
        fun setUp() {
            exportLogger = LoggerFactory.getLogger(ExportService::class.java) as Logger
            logAppender.start()
            exportLogger.addAppender(logAppender)
            sourceDir = tempDir.resolve("skills/a-skill").toFile()
            sourceDir.mkdirs()
            sourceDir.resolve("helper.md").writeText("Next to the manifest.\n")
            skillDir = tempDir.resolve("home/.claude/skills/a-skill").toFile()
            tempDir.resolve("elsewhere").toFile().mkdirs()
            tempDir.resolve("elsewhere/secret.md").toFile().writeText("From elsewhere.\n")
        }

        @AfterEach
        fun tearDown() {
            exportLogger.detachAppender(logAppender)
            logAppender.stop()
            logAppender.list.clear()
        }

        @Test
        fun `should warn naming the skill and both paths when a source is absolute`() {
            // given
            val outside = tempDir.resolve("elsewhere/secret.md").toFile()

            // when
            exportService.copySkillFiles(
                listOf(SkillFile(source = outside.absolutePath, target = "secret.md")),
                sourceDir,
                skillDir,
                skillId = "a-skill",
            )

            // then
            assertThat(skillDir.resolve("secret.md")).hasContent("From elsewhere.\n")
            assertThat(warnings()).anyMatch {
                it.contains("a-skill") && it.contains(outside.absolutePath)
            }
        }

        @Test
        fun `should warn naming the skill and both paths when a source climbs out of its directory`() {
            // when
            exportService.copySkillFiles(
                listOf(SkillFile(source = "../../elsewhere/secret.md", target = "secret.md")),
                sourceDir,
                skillDir,
                skillId = "a-skill",
            )

            // then
            assertThat(skillDir.resolve("secret.md")).hasContent("From elsewhere.\n")
            assertThat(warnings()).anyMatch {
                it.contains("a-skill") && it.contains("../../elsewhere/secret.md")
            }
        }

        @Test
        fun `should not warn when a source sits inside the directory of its own skill`() {
            // when
            exportService.copySkillFiles(
                listOf(SkillFile(source = "helper.md", target = "helper.md")),
                sourceDir,
                skillDir,
                skillId = "a-skill",
            )

            // then
            assertThat(skillDir.resolve("helper.md")).hasContent("Next to the manifest.\n")
            assertThat(warnings()).isEmpty()
        }

        private fun warnings() = logAppender.list.filter { it.level == Level.WARN }.map { it.formattedMessage }
    }

    /**
     * A dry run goes through the same service as a deploy and differs only in its sink: every artifact is still
     * rendered and every companion file still resolved, so every authoring error a deploy reports is reported here
     * too, while nothing reaches the disk.
     */
    @Nested
    inner class DryRun {

        private val logAppender = ListAppender<ILoggingEvent>()
        private lateinit var sinkLogger: Logger
        private lateinit var dryRunService: ExportService
        private lateinit var sourceDir: File
        private lateinit var skillDir: File

        @BeforeEach
        fun setUp() {
            sinkLogger = LoggerFactory.getLogger(DryRunArtifactSink::class.java) as Logger
            logAppender.start()
            sinkLogger.addAppender(logAppender)
            dryRunService = ExportService(DryRunArtifactSink)
            sourceDir = tempDir.resolve("skills/a-skill").toFile()
            sourceDir.mkdirs()
            sourceDir.resolve("helper.md").writeText("Next to the manifest.\n")
            skillDir = tempDir.resolve("home/.claude/skills/a-skill").toFile()
        }

        @AfterEach
        fun tearDown() {
            sinkLogger.detachAppender(logAppender)
            logAppender.stop()
            logAppender.list.clear()
        }

        @Test
        fun `should render the artifact without writing it`() {
            // given
            var rendered = false

            // when
            dryRunService.export(ruleset, targetFile) { output ->
                output.appendLine("# header")
                rendered = true
            }

            // then
            // - the printer ran to completion, which is what makes a dry run report the same failures a deploy does
            assertThat(rendered).isTrue()
            assertThat(targetFile).doesNotExist()
            // - not even the directory that would hold the artifact is created
            assertThat(targetFile.parentFile).doesNotExist()
        }

        @Test
        fun `should name the absolute target path of the artifact it would write`() {
            // when
            dryRunService.export(ruleset, targetFile) { output -> output.appendLine("content") }

            // then
            assertThat(infos()).anyMatch {
                it.contains("Would export") && it.contains(ruleset.id) && it.contains(targetFile.absolutePath)
            }
        }

        @Test
        fun `should propagate the failure of the output consumer without writing anything`() {
            // when
            val error = runCatching {
                dryRunService.export(ruleset, targetFile) { output ->
                    output.appendLine("partial")
                    error("resolution failed while printing the body")
                }
            }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(IllegalStateException::class.java)
                .hasMessageContaining("resolution failed while printing the body")
            assertThat(targetFile.parentFile).doesNotExist()
        }

        @Test
        fun `should name both paths of a skill file it would copy without copying it`() {
            // when
            dryRunService.copySkillFiles(
                listOf(SkillFile(source = "helper.md", target = "helper.md")),
                sourceDir,
                skillDir,
                skillId = "a-skill",
            )

            // then
            assertThat(skillDir).doesNotExist()
            assertThat(infos()).anyMatch {
                it.contains("Would copy") &&
                    it.contains(sourceDir.resolve("helper.md").absolutePath) &&
                    it.contains(skillDir.resolve("helper.md").absolutePath)
            }
        }

        @Test
        fun `should fail with the source path when the declared skill file does not exist`() {
            // when
            val error = runCatching {
                dryRunService.copySkillFiles(listOf(SkillFile("missing.md", "missing.md")), sourceDir, skillDir)
            }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillFileResolvingException::class.java)
                .hasMessageContaining(sourceDir.resolve("missing.md").absolutePath)
        }

        @Test
        fun `should fail with the declared path when a relative skill file has no source directory`() {
            // when
            val error = runCatching {
                dryRunService.copySkillFiles(listOf(SkillFile("helper.md", "helper.md")), null, skillDir)
            }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillFileResolvingException::class.java)
                .hasMessageContaining("Cannot resolve relative skill file 'helper.md'")
        }

        @Test
        fun `should refuse a skill file whose target climbs out of the skill directory`() {
            // when
            val error = runCatching {
                dryRunService.copySkillFiles(
                    listOf(SkillFile(source = "helper.md", target = "../../../.bashrc")),
                    sourceDir,
                    skillDir,
                )
            }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(SkillFileResolvingException::class.java)
                .hasMessageContaining("../../../.bashrc")
        }

        @Test
        fun `should keep an artifact directory it would replace`() {
            // given
            skillDir.mkdirs()
            skillDir.resolve("leftover.md").writeText("From an earlier deploy.\n")

            // when
            dryRunService.replaceArtifactDirectory(skillDir, owned = skillDir.parentFile, describedBy = "skill 'a-skill'")

            // then
            assertThat(skillDir.resolve("leftover.md")).hasContent("From an earlier deploy.\n")
            assertThat(infos()).anyMatch { it.contains("Would remove") && it.contains(skillDir.absolutePath) }
        }

        @Test
        fun `should still refuse to replace a directory outside the owned one`() {
            // given
            // - the guard that stops a deploy from deleting outside its scope is a failure a dry run has to report too
            val escaping = skillDir.parentFile.resolve("../escaped")

            // when
            val error = runCatching {
                dryRunService.replaceArtifactDirectory(escaping, owned = skillDir.parentFile, describedBy = "skill 'escaping'")
            }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(ArtifactPathException::class.java)
        }

        private fun infos() = logAppender.list.filter { it.level == Level.INFO }.map { it.formattedMessage }
    }

    @Test
    fun `should delete an artifact directory it replaces`() {
        // given
        val skillsDir = tempDir.resolve("home/.claude/skills").toFile()
        val skillDir = skillsDir.resolve("a-skill")
        skillDir.mkdirs()
        skillDir.resolve("leftover.md").writeText("From an earlier deploy.\n")

        // when
        exportService.replaceArtifactDirectory(skillDir, owned = skillsDir, describedBy = "skill 'a-skill'")

        // then
        assertThat(skillDir).doesNotExist()
        assertThat(skillsDir).exists()
    }

    @Test
    fun `should refuse to replace a directory outside the owned one`() {
        // given
        val skillsDir = tempDir.resolve("home/.claude/skills").toFile()
        val escaping = tempDir.resolve("home/escaped").toFile()
        escaping.mkdirs()

        // when
        val error = runCatching {
            exportService.replaceArtifactDirectory(escaping, owned = skillsDir, describedBy = "skill 'escaping'")
        }.exceptionOrNull()

        // then
        assertThat(error).isInstanceOf(ArtifactPathException::class.java)
        assertThat(escaping).exists()
    }

    @Test
    fun `should refuse to copy a skill file whose target climbs out of the skill directory`() {
        // given
        // - 'target' is free-form manifest text, and the directory it resolves against now sits in the user's home
        val sourceDir = tempDir.resolve("skill-source").toFile()
        sourceDir.mkdirs()
        sourceDir.resolve("payload.md").writeText("Payload.\n")
        val skillDir = tempDir.resolve("home/.claude/skills/a-skill").toFile()
        val victim = tempDir.resolve("home/.bashrc").toFile()
        victim.parentFile.mkdirs()
        victim.writeText("Untouched.\n")

        // when
        val error = runCatching {
            exportService.copySkillFiles(
                listOf(SkillFile(source = "payload.md", target = "../../../.bashrc")),
                sourceDir,
                skillDir,
            )
        }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(SkillFileResolvingException::class.java)
            .hasMessageContaining("../../../.bashrc")
        assertThat(victim).hasContent("Untouched.\n")
    }

    @Test
    fun `should copy a skill file into a subdirectory of the skill directory`() {
        // given
        // - a target may still nest, which is what 'templates/example.txt' in the manifests relies on
        val sourceDir = tempDir.resolve("skill-source").toFile()
        sourceDir.mkdirs()
        sourceDir.resolve("payload.md").writeText("Payload.\n")
        val skillDir = tempDir.resolve("skills/a-skill").toFile()

        // when
        exportService.copySkillFiles(
            listOf(SkillFile(source = "payload.md", target = "templates/example.md")),
            sourceDir,
            skillDir,
        )

        // then
        assertThat(skillDir.resolve("templates/example.md")).hasContent("Payload.\n")
    }

    @Test
    fun `should write exported content when output consumer succeeds`() {
        // when
        exportService.export(ruleset, targetFile) { output ->
            output.appendLine("# header")
            output.appendLine("body")
        }

        // then
        assertThat(targetFile).exists()
        assertThat(targetFile.readText()).isEqualTo("# header\nbody\n")
    }

    @Test
    fun `should replace existing target content when output consumer succeeds`() {
        // given
        // - a stale artifact from a previous export at the same path
        targetFile.parentFile.mkdirs()
        targetFile.writeText("stale content from a previous run")

        // when
        exportService.export(ruleset, targetFile) { output ->
            output.appendLine("fresh")
        }

        // then
        assertThat(targetFile.readText()).isEqualTo("fresh\n")
    }

    @Test
    fun `should not create target file when output consumer fails mid-write`() {
        // when
        val error = runCatching {
            exportService.export(ruleset, targetFile) { output ->
                output.appendLine("---")
                output.appendLine("frontmatter written")
                error("resolution failed while printing the body")
            }
        }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("resolution failed while printing the body")
        assertThat(targetFile).doesNotExist()
    }

    @Test
    fun `should keep previous target content when output consumer fails mid-write`() {
        // given
        // - a complete artifact from a previous successful export
        targetFile.parentFile.mkdirs()
        targetFile.writeText("previous complete content")

        // when
        runCatching {
            exportService.export(ruleset, targetFile) { output ->
                output.appendLine("partial")
                error("resolution failed while printing the body")
            }
        }

        // then
        assertThat(targetFile.readText()).isEqualTo("previous complete content")
    }

    @Test
    fun `should not leave temporary files behind when output consumer fails`() {
        // when
        runCatching {
            exportService.export(ruleset, targetFile) { output ->
                output.appendLine("partial")
                error("resolution failed while printing the body")
            }
        }

        // then
        assertThat(targetFile.parentFile.listFiles()).isEmpty()
    }

    @Test
    fun `should not leave temporary files behind when export succeeds`() {
        // when
        exportService.export(ruleset, targetFile) { output ->
            output.appendLine("content")
        }

        // then
        assertThat(targetFile.parentFile.listFiles()).containsExactly(targetFile)
    }

    @Test
    fun `should copy the declared skill file when it exists next to the manifest`() {
        // given
        // - a directory-based skill: its companion file is resolved against the skill's own directory
        val sourceDir = tempDir.resolve("skill").toFile()
        sourceDir.mkdirs()
        sourceDir.resolve("helper.md").writeText("Companion content.\n")
        val targetDir = tempDir.resolve("exported").toFile()

        // when
        exportService.copySkillFiles(listOf(SkillFile("helper.md", "helper.md")), sourceDir, targetDir)

        // then
        assertThat(targetDir.resolve("helper.md").readText()).isEqualTo("Companion content.\n")
    }

    @Test
    fun `should fail with the declared path when a relative skill file has no source directory`() {
        // given
        // - a standalone skill YAML has no directory of its own to resolve the file against
        val targetDir = tempDir.resolve("exported").toFile()

        // when
        val error = runCatching {
            exportService.copySkillFiles(listOf(SkillFile("helper.md", "helper.md")), null, targetDir)
        }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(SkillFileResolvingException::class.java)
            .hasMessageContaining("Cannot resolve relative skill file 'helper.md'")
    }

    @Test
    fun `should fail with the source path when the declared skill file does not exist`() {
        // given
        val sourceDir = tempDir.resolve("skill").toFile()
        sourceDir.mkdirs()
        val targetDir = tempDir.resolve("exported").toFile()

        // when
        val error = runCatching {
            exportService.copySkillFiles(listOf(SkillFile("missing.md", "missing.md")), sourceDir, targetDir)
        }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(SkillFileResolvingException::class.java)
            .hasMessageContaining(sourceDir.resolve("missing.md").absolutePath)
    }
}
