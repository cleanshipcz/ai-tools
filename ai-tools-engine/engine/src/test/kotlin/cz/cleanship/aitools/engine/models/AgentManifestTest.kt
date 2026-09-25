package cz.cleanship.aitools.engine.models

import com.charleskorn.kaml.Yaml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AgentManifestTest {

    @Test
    fun `should decode the MCP servers an agent uses, in the order it names them`() {
        // given
        val input = """
            |id: reviewer
            |description: Reviews code
            |persona: A reviewer
            |prompt: Review the change
            |mcps: [github, atlassian]
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when
        val result = Yaml.default.decodeFromString(AgentManifest.serializer(), input)

        // then
        assertThat(result.mcps).containsExactly("github", "atlassian")
    }

    @Test
    fun `should use no MCP server when the agent names none`() {
        // given
        val input = """
            |id: reviewer
            |description: Reviews code
            |persona: A reviewer
            |prompt: Review the change
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when
        val result = Yaml.default.decodeFromString(AgentManifest.serializer(), input)

        // then
        assertThat(result.mcps).isEmpty()
    }
}
