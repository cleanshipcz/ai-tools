package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectContext
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectDocumentation
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        // - Codex and Cursor keep their MCP config file, .codex/config.toml and .cursor/mcp.json, beside the directories they generate
        "CODEX, .codex/skills;.codex/features",
        "CURSOR, .cursor/rules;.cursor/commands;.cursor/features",
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
            replaced.parentFile.mkdirs()
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

    @ParameterizedTest
    @CsvSource(
        "GITHUB_COPILOT, .vscode/mcp.json",
        "CLAUDE, .mcp.json",
        "CODEX, .codex/config.toml",
        "CURSOR, .cursor/mcp.json",
    )
    fun `should name the MCP config file its tool reads in a project`(toolType: ToolType, expected: String) {
        // given
        val projectDir = tempDir.resolve("project")
        val adapter = ToolFactory.create(toolType)

        // when
        val exporter = adapter.mcpConfig(projectDir)

        // then
        assertThat(exporter?.file).isEqualTo(projectDir.resolve(expected))
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "CLAUDE | .claude .claude/agents .claude/commands .claude/skills .claude/workflows",
            "CODEX | .codex .codex/skills .codex/features",
            "GITHUB_COPILOT | .github .github/agents .github/prompts .github/instructions",
            "CURSOR | .cursor .cursor/rules .cursor/commands .cursor/features",
            "WINDSURF | .windsurf .windsurf/rules .windsurf/workflows",
            "ANTIGRAVITY | .agent .agent/rules .agent/workflows",
        ],
    )
    fun `should name every directory the files of a tool land in besides the files at the root of the project, each after the directory holding it`(
        toolType: ToolType,
        expected: String,
    ) {
        // given
        val projectDir = tempDir.resolve("project")

        // when
        val directories = ToolFactory.create(toolType).toolDirectories(projectDir)

        // then
        assertThat(directories).containsExactlyElementsOf(expected.split(" ").map { projectDir.resolve(it) })
    }

    /**
     * What of the MCP support of the engine each tool lacks is decided by its adapter, and reported with the reason it gives.
     */
    @ParameterizedTest
    @CsvSource(
        // - tool, attaches servers to agents, applies allowed tools, applies denied tools, has user-scope MCP
        "CLAUDE, true, false, true, true",
        "CODEX, false, true, true, true",
        "GITHUB_COPILOT, false, false, false, false",
        "CURSOR, false, false, false, false",
        "WINDSURF, false, true, true, false",
        "ANTIGRAVITY, false, true, true, false",
    )
    fun `should give a reason for every part of the MCP support a tool lacks`(
        toolType: ToolType,
        attachesServers: Boolean,
        appliesAllowed: Boolean,
        appliesDenied: Boolean,
        hasUserScope: Boolean,
    ) {
        // when
        val limits = ToolFactory.create(toolType).mcpLimits

        // then
        // - Windsurf and Antigravity get no MCP file at all, which is reported on its own, so they give no reason about restrictions
        assertThat(limits.agentServers == null).isEqualTo(attachesServers)
        assertThat(limits.allowedTools == null).isEqualTo(appliesAllowed)
        assertThat(limits.deniedTools == null).isEqualTo(appliesDenied)
        assertThat(limits.userScope == null).isEqualTo(hasUserScope)
    }

    @Test
    fun `should give the reason Claude Code applies no allowed tools of a server`() {
        // when
        val reason = ToolFactory.create(ToolType.CLAUDE).mcpLimits.allowedTools

        // then
        assertThat(reason).isEqualTo("Claude Code has no list of the tools a server may offer; only 'deny' is rendered")
    }

    @Test
    fun `should give the reason GitHub Copilot attaches no MCP server to an agent`() {
        // when
        val reason = ToolFactory.create(ToolType.GITHUB_COPILOT).mcpLimits.agentServers

        // then
        assertThat(reason).isEqualTo("a Copilot agent without 'tools' already gets every configured server, and a 'tools' list would remove its built-in tools")
    }

    @ParameterizedTest
    @CsvSource(
        "WINDSURF, ~/.codeium/windsurf/mcp_config.json",
        "WINDSURF, Devin Desktop",
        "ANTIGRAVITY, environment variable",
    )
    fun `should name the facts that keep a tool out of the MCP servers of the user scope`(
        toolType: ToolType,
        fact: String,
    ) {
        // when
        val reason = ToolFactory.create(toolType).mcpLimits.userScope

        // then
        assertThat(reason).contains(fact)
    }

    @ParameterizedTest
    @EnumSource(value = ToolType::class, names = ["WINDSURF", "ANTIGRAVITY"])
    fun `should name no MCP config file for a tool whose MCP support is not implemented`(toolType: ToolType) {
        // given
        val adapter = ToolFactory.create(toolType)

        // when
        val exporter = adapter.mcpConfig(tempDir.resolve("project"))

        // then
        assertThat(exporter).isNull()
    }

    @ParameterizedTest
    @EnumSource(ToolType::class)
    fun `should keep every MCP config file when the project replaces`(toolType: ToolType) {
        // given
        // - the MCP config file of every tool, each holding servers the user added by hand next to the owned ones
        val projectDir = tempDir.resolve("project")
        val mcpFiles = listOf(".mcp.json", ".vscode/mcp.json", ".cursor/mcp.json", ".codex/config.toml")
            .map { writeFile(projectDir.resolve(it)) }
        val adapter = ToolFactory.create(toolType)
        val manifest = project(replace = true)
        // - an earlier export left a file in every replaced path
        adapter.replacedPaths(projectDir, manifest).forEach { writeFile(it.resolve("stale.md")) }

        // when
        adapter.prepare(projectDir, manifest)

        // then
        assertThat(mcpFiles).allSatisfy { assertThat(it).hasContent("Written by hand.\n") }
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
