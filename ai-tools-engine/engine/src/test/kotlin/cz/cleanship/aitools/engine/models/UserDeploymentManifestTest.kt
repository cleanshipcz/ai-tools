package cz.cleanship.aitools.engine.models

import com.charleskorn.kaml.PolymorphismStyle
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UserDeploymentManifestTest {

    // The filters are a sealed hierarchy discriminated by a 'type' property, so the manifest is decoded with the
    // very configuration cz.cleanship.aitools.engine.services.LoaderService reads it with.
    private val yaml = Yaml(configuration = YamlConfiguration(polymorphismStyle = PolymorphismStyle.Property))

    @Test
    fun `should deserialize a manifest declaring every block`() {
        val input = """
            |id: globals
            |description: My global AI tool setup
            |tools:
            |    - claude
            |    - codex
            |replace: true
            |rulesets:
            |    filter:
            |        - type: tags
            |          tags: [global]
            |skills:
            |    filter:
            |        - type: whitelist
            |          ids: [run-detekt]
            |agents:
            |    filter:
            |        - type: blacklist
            |          ids: [code-reviewer]
            |prompts:
            |    filter:
            |        - type: tags
            |          tags: [git]
            |fragments:
            |    filter: []
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        val result = yaml.decodeFromString(UserDeploymentManifest.serializer(), input)

        assertThat(result.id).isEqualTo("globals")
        assertThat(result.description).isEqualTo("My global AI tool setup")
        assertThat(result.tools).containsExactly(ToolType.CLAUDE, ToolType.CODEX)
        assertThat(result.replace).isTrue()
        assertThat(result.rulesets.filter).containsExactly(ProjectFilter.ByTags(listOf("global")))
        assertThat(result.skills.filter).containsExactly(ProjectFilter.ByWhitelistedIds(listOf("run-detekt")))
        assertThat(result.agents.filter).containsExactly(ProjectFilter.ByBlacklistedIds(listOf("code-reviewer")))
        assertThat(result.prompts.filter).containsExactly(ProjectFilter.ByTags(listOf("git")))
        assertThat(result.fragments.filter).isEmpty()
        assertThat(result.metadata).isEqualTo(ManifestMetadata(version = Version("1.0.0")))
    }

    @Test
    fun `should deserialize a manifest declaring nothing but the required fields`() {
        val input = """
            |id: globals
            |description: My global AI tool setup
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        val result = yaml.decodeFromString(UserDeploymentManifest.serializer(), input)

        // an absent list is null rather than empty, because omission means every configured tool while an empty list means none
        assertThat(result.tools).isNull()
        assertThat(result.replace).isFalse()
        assertThat(result.rulesets.filter).isEmpty()
        assertThat(result.skills.filter).isEmpty()
        assertThat(result.agents.filter).isEmpty()
        assertThat(result.prompts.filter).isEmpty()
        assertThat(result.fragments.filter).isEmpty()
    }

    @Test
    fun `should deserialize a valueless tools key as no restriction`() {
        // a key with nothing under it - what commenting out the last entry leaves behind - is not an empty list,
        // so it widens the deployment back to every configured tool instead of restricting it to none
        val input = """
            |id: globals
            |description: My global AI tool setup
            |tools:
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        val result = yaml.decodeFromString(UserDeploymentManifest.serializer(), input)

        assertThat(result.tools).isNull()
    }

    @Test
    fun `should deserialize an empty tools list as a restriction to no tool`() {
        val input = """
            |id: globals
            |description: My global AI tool setup
            |tools: []
            |metadata:
            |    version: 1.0.0
        """.trimMargin()

        val result = yaml.decodeFromString(UserDeploymentManifest.serializer(), input)

        assertThat(result.tools).isEmpty()
    }

    @ParameterizedTest
    @CsvSource("context", "directory", "features", "deploy", "type")
    fun `should reject a manifest carrying a field the user scope does not have`(field: String) {
        // the user scope has no repository to describe, no directory to choose and no features to deploy, and the
        // filename already names the kind - so any of these is a mistake to report rather than a key to ignore
        val input = """
            |id: globals
            |description: My global AI tool setup
            |metadata:
            |    version: 1.0.0
            |$field: something
        """.trimMargin()

        val error = runCatching { yaml.decodeFromString(UserDeploymentManifest.serializer(), input) }.exceptionOrNull()

        assertThat(error).hasMessageContaining(field)
    }
}
