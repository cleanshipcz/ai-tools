package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectContext
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectDocumentation
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * The project-scope contract every adapter shares: what a replacing deploy deletes, and that the deletion never reaches through a symbolic link.
 */
class ToolAdapterTest {

    @TempDir
    lateinit var tempDir: File

    @ParameterizedTest
    @CsvSource(
        "WINDSURF, .windsurf",
        "ANTIGRAVITY, .agent",
        "GITHUB_COPILOT, .github/prompts;.github/instructions;.github/agents",
        "CLAUDE, .claude",
        "CODEX, .codex",
        "CURSOR, .cursor",
    )
    fun `should name the directories of its tool as the paths a replacing project deploy deletes`(
        toolType: ToolType,
        expected: String,
    ) {
        // given
        val projectDir = tempDir.resolve("project")
        val adapter = ToolFactory.create(toolType)

        // when
        val replacedPaths = adapter.replacedPaths(projectDir, project(replace = true))

        // then
        assertThat(replacedPaths).containsExactlyElementsOf(expected.split(";").map { projectDir.resolve(it) })
    }

    @ParameterizedTest
    @EnumSource(ToolType::class)
    fun `should name no path when the project does not replace`(toolType: ToolType) {
        // given
        val adapter = ToolFactory.create(toolType)

        // when
        val replacedPaths = adapter.replacedPaths(tempDir.resolve("project"), project(replace = false))

        // then
        assertThat(replacedPaths).isEmpty()
    }

    @ParameterizedTest
    @EnumSource(ToolType::class)
    fun `should delete every replaced path and nothing beside it when the project replaces`(toolType: ToolType) {
        // given
        val projectDir = tempDir.resolve("project")
        val adapter = ToolFactory.create(toolType)
        val manifest = project(replace = true)
        val replacedPaths = adapter.replacedPaths(projectDir, manifest)
        // - an earlier export left a file in every replaced path
        replacedPaths.forEach { writeFile(it.resolve("nested/stale.md")) }
        // - files of the project that no tool generates
        val kept = listOf(
            writeFile(projectDir.resolve("README.md")),
            writeFile(projectDir.resolve(".github/workflows/ci.yml")),
            writeFile(projectDir.resolve("src/main.kt")),
        )

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(replacedPaths).allSatisfy { assertThat(it).doesNotExist() }
        assertThat(kept).allSatisfy { assertThat(it).exists() }
    }

    @ParameterizedTest
    @EnumSource(ToolType::class)
    fun `should remove a link inside a replaced path as a link and keep what it leads to`(toolType: ToolType) {
        // given
        val projectDir = tempDir.resolve("project")
        val adapter = ToolFactory.create(toolType)
        val manifest = project(replace = true)
        // - a folder outside the project, linked into every directory the deploy replaces
        val outsideFile = writeFile(tempDir.resolve("outside/SKILL.md"))
        val links = adapter.replacedPaths(projectDir, manifest).map { replaced ->
            replaced.resolve("skills").mkdirs()
            Files.createSymbolicLink(replaced.resolve("skills/linked").toPath(), outsideFile.parentFile.toPath()).toFile()
        }

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(outsideFile).hasContent("Written by hand.\n")
        assertThat(links).allSatisfy { assertThat(Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS)).isFalse() }
    }

    @ParameterizedTest
    @EnumSource(ToolType::class)
    fun `should remove a replaced path that is itself a link as a link and keep what it leads to`(toolType: ToolType) {
        // given
        val projectDir = tempDir.resolve("project")
        projectDir.resolve(".github").mkdirs()
        val adapter = ToolFactory.create(toolType)
        val manifest = project(replace = true)
        val replacedPaths = adapter.replacedPaths(projectDir, manifest)
        // - every replaced path is a link to a folder of its own outside the project
        val outsideFiles = replacedPaths.mapIndexed { index, replaced ->
            val outsideFile = writeFile(tempDir.resolve("outside-$index/SKILL.md"))
            Files.createSymbolicLink(replaced.toPath(), outsideFile.parentFile.toPath())
            outsideFile
        }

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(outsideFiles).allSatisfy { assertThat(it).hasContent("Written by hand.\n") }
        assertThat(replacedPaths).allSatisfy { assertThat(Files.exists(it.toPath(), LinkOption.NOFOLLOW_LINKS)).isFalse() }
    }

    private fun project(replace: Boolean) = ProjectManifest(
        id = "test-project",
        description = "Test Project",
        metadata = ManifestMetadata(version = Version("1.0.0")),
        context = ProjectContext(documentation = ProjectDocumentation(readme = "README.md")),
        deploy = ProjectDeploy(directory = tempDir.resolve("project").absolutePath, replace = replace),
    )

    private fun writeFile(file: File): File {
        file.parentFile.mkdirs()
        file.writeText("Written by hand.\n")
        return file
    }
}
