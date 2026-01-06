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
}
