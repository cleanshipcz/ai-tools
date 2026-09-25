package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.McpToolRestriction
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DeploymentMcpCheckTest {

    private val subject = "Project 'p'"
    private val field = "deploy.mcps"

    @Test
    fun `should find no problem when every agent and every restriction names a selected server with valid tool names`() {
        // given
        val agents = listOf(agent("reviewer", "github"), agent("writer"))
        val restrictions =
            mapOf("github" to McpToolRestriction(allow = listOf("get_me"), deny = listOf("delete_repository", "a.b-c_1")))

        // when
        val problems = mcpProblems(subject, field, agents, selected = setOf("github", "atlassian"), restrictions)

        // then
        assertThat(problems).isEmpty()
    }

    @Test
    fun `should name the deployment, the agent and the server once for each server an agent uses but the deployment does not select`() {
        // given
        // - the agent names github twice and atlassian once, and the deployment selects neither
        val agents = listOf(agent("reviewer", "github", "atlassian", "github"))

        // when
        val problems = mcpProblems(subject, field, agents, selected = emptySet(), restrictions = emptyMap())

        // then
        assertThat(problems).containsExactly(
            "Project 'p' deploys the agent 'reviewer', which uses the MCP server 'github', but does not select that server under 'deploy.mcps'. Select the server, or leave the agent out.",
            "Project 'p' deploys the agent 'reviewer', which uses the MCP server 'atlassian', but does not select that server under 'deploy.mcps'. Select the server, or leave the agent out.",
        )
    }

    @Test
    fun `should name a restricted server the deployment does not select`() {
        // when
        val problems =
            mcpProblems("User deployment 'u'", "mcps", emptyList(), selected = setOf("atlassian"), mapOf("github" to McpToolRestriction(deny = listOf("x"))))

        // then
        assertThat(problems).containsExactly(
            "User deployment 'u' restricts the tools of the MCP server 'github' under 'mcps.tools', but does not select that server. Select it under 'mcps', or remove the restriction.",
        )
    }

    @ParameterizedTest
    @CsvSource(
        // - a space
        "'get me'",
        // - a character a permission rule reads as syntax
        "'get_me(x)'",
        // - a wildcard
        "'*'",
        // - nothing at all
        "''",
    )
    fun `should name the server of a tool outside the grammar of MCP tool names, never the tool itself`(tool: String) {
        // when
        val problems =
            mcpProblems(subject, field, emptyList(), selected = setOf("github"), mapOf("github" to McpToolRestriction(deny = listOf(tool))))

        // then
        assertThat(problems).singleElement().satisfies({
            assertThat(it).contains("MCP server 'github'").contains("A-Z a-z 0-9 _ - .")
            if (tool.isNotEmpty()) assertThat(it).doesNotContain(tool)
        })
    }

    @Test
    fun `should refuse a tool name one character longer than the longest length`() {
        // when
        val problems =
            mcpProblems(subject, field, emptyList(), selected = setOf("github"), mapOf("github" to McpToolRestriction(allow = listOf("x".repeat(129)))))

        // then
        assertThat(problems).singleElement().satisfies({ assertThat(it).contains("MCP server 'github'").contains("1 to 128") })
    }

    @Test
    fun `should accept a tool name of exactly the longest length`() {
        // when
        val problems =
            mcpProblems(subject, field, emptyList(), selected = setOf("github"), mapOf("github" to McpToolRestriction(allow = listOf("x".repeat(128)))))

        // then
        assertThat(problems).isEmpty()
    }

    @ParameterizedTest
    @CsvSource(
        // - server id, tool, the part of the message that names what holds the separator
        "a__b, get_me, whose id holds '__'",
        "github, get__me, that holds '__'",
    )
    fun `should refuse a restricted server or tool holding the separator of a permission entry`(
        server: String,
        tool: String,
        expected: String,
    ) {
        // when
        val problems =
            mcpProblems(subject, field, emptyList(), selected = setOf(server), mapOf(server to McpToolRestriction(deny = listOf(tool))))

        // then
        assertThat(problems).singleElement().satisfies({ assertThat(it).contains("MCP server '$server'").contains(expected) })
    }

    @Test
    fun `should name a restricted server id with its line breaks escaped, so it can never forge a line of its own`() {
        // given
        // - a key of the restriction map, which no grammar checks before it is named
        val server = "x\nERROR forged"

        // when
        val problems =
            mcpProblems(subject, field, emptyList(), selected = emptySet(), mapOf(server to McpToolRestriction(deny = listOf("a"))))

        // then
        assertThat(problems).singleElement().satisfies({
            assertThat(it).contains("'x\\u000AERROR forged'").doesNotContain("\n")
        })
    }

    private fun agent(id: String, vararg mcps: String) =
        AgentManifest(id = id, description = id, persona = "A persona", prompt = "A prompt", mcps = mcps.toList(), metadata = ManifestMetadata(version = Version("1.0.0")))
}
