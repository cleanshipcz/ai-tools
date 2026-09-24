package cz.cleanship.aitools.engine.models

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import com.charleskorn.kaml.YamlException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class SkillManifestTest {

    // Decoded with the configuration cz.cleanship.aitools.engine.services.LoaderService reads manifests with, so that strictness here is the strictness a deploy applies.
    private val yaml = Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property))

    @Test
    fun `should decode a pointer manifest declaring a source and no description`() {
        // given
        val input = """
            |id: jira-ticket
            |source: ${'$'}{PROJECTS_FOLDER}/jira-confluence-mcp-server/skills/jira-ticket
            |metadata:
            |    version: 2.0.0
            |    tags: [jira, atlassian]
        """.trimMargin()

        // when
        val result = yaml.decodeFromString(SkillManifest.serializer(), input)

        // then
        assertThat(result.id).isEqualTo("jira-ticket")
        assertThat(result.source).isEqualTo("\${PROJECTS_FOLDER}/jira-confluence-mcp-server/skills/jira-ticket")
        assertThat(result.description).isEmpty()
        assertThat(result.sections).isEmpty()
        assertThat(result.files).isEmpty()
        assertThat(result.body).isNull()
        assertThat(result.metadata.tags).containsExactlyInAnyOrder("jira", "atlassian")
    }

    @Test
    fun `should decode a skill without a source as before`() {
        // given
        val input = """
            |id: run-detekt
            |description: Run Detekt
            |sections:
            |    - text: Run it.
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        // when
        val result = yaml.decodeFromString(SkillManifest.serializer(), input)

        // then
        assertThat(result.source).isNull()
        assertThat(result.body).isNull()
        assertThat(result.description).isEqualTo("Run Detekt")
        assertThat(result.sections).containsExactly(SkillSection.TextSection("Run it."))
    }

    @ParameterizedTest
    @CsvSource(
        // - the rendered body is read from the SKILL.md of the source, never from the manifest itself
        "body",
        // - a misspelling of the new field is an unknown key like any other
        "sources",
    )
    fun `should reject a key the model does not declare`(key: String) {
        // given
        val input = """
            |id: jira-ticket
            |source: ../plain/jira-ticket
            |$key: something
            |metadata:
            |    version: 2.0.0
        """.trimMargin()

        // when / then
        assertThatThrownBy { yaml.decodeFromString(SkillManifest.serializer(), input) }
            .isInstanceOf(YamlException::class.java)
            .hasMessageContaining(key)
    }
}
