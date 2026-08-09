package cz.cleanship.aitools.engine.models

import com.charleskorn.kaml.Yaml
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ProjectManifestTest {

    private val yaml = Yaml.default

    @Test
    fun `should deserialize manifest with replace=false by default`() {
        val input = """
            |id: test-project
            |description: Test Project
            |metadata:
            |    version: 1.0.0
            |context:
            |    documentation:
            |        readme: README.md
            |deploy:
            |    directory: /tmp/test
        """.trimMargin()

        val result = yaml.decodeFromString(ProjectManifest.serializer(), input)

        assertThat(result.deploy.replace).isFalse()
    }

    @Test
    fun `should deserialize manifest with replace=true`() {
        val input = """
            |id: test-project
            |description: Test Project
            |metadata:
            |    version: 1.0.0
            |context:
            |    documentation:
            |        readme: README.md
            |deploy:
            |    directory: /tmp/test
            |    replace: true
        """.trimMargin()

        val result = yaml.decodeFromString(ProjectManifest.serializer(), input)

        assertThat(result.deploy.replace).isTrue()
    }

    @Test
    fun `should deserialize manifest with no tools by default`() {
        val input = """
            |id: test-project
            |description: Test Project
            |metadata:
            |    version: 1.0.0
            |context:
            |    documentation:
            |        readme: README.md
            |deploy:
            |    directory: /tmp/test
        """.trimMargin()

        val result = yaml.decodeFromString(ProjectManifest.serializer(), input)

        // an absent list is null rather than empty, because omission means every configured tool while an empty list means none
        assertThat(result.deploy.tools).isNull()
    }

    @Test
    fun `should deserialize manifest with the declared tools`() {
        val input = """
            |id: test-project
            |description: Test Project
            |metadata:
            |    version: 1.0.0
            |context:
            |    documentation:
            |        readme: README.md
            |deploy:
            |    directory: /tmp/test
            |    tools:
            |        - claude
            |        - github_copilot
        """.trimMargin()

        val result = yaml.decodeFromString(ProjectManifest.serializer(), input)

        assertThat(result.deploy.tools).containsExactly(ToolType.CLAUDE, ToolType.GITHUB_COPILOT)
    }

    @Test
    fun `should deserialize a valueless tools key as no restriction`() {
        // a key with nothing under it - what commenting out the last entry leaves behind - is not an empty list,
        // so it widens the project back to every configured tool instead of restricting it to none
        val input = """
            |id: test-project
            |description: Test Project
            |metadata:
            |    version: 1.0.0
            |context:
            |    documentation:
            |        readme: README.md
            |deploy:
            |    directory: /tmp/test
            |    tools:
        """.trimMargin()

        val result = yaml.decodeFromString(ProjectManifest.serializer(), input)

        assertThat(result.deploy.tools).isNull()
    }

    @Test
    fun `should deserialize manifest with an empty tools list`() {
        val input = """
            |id: test-project
            |description: Test Project
            |metadata:
            |    version: 1.0.0
            |context:
            |    documentation:
            |        readme: README.md
            |deploy:
            |    directory: /tmp/test
            |    tools: []
        """.trimMargin()

        val result = yaml.decodeFromString(ProjectManifest.serializer(), input)

        assertThat(result.deploy.tools).isEmpty()
    }
}
