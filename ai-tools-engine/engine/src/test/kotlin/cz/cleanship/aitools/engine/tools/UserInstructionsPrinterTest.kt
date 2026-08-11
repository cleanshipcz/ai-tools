package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.ruleset
import cz.cleanship.aitools.engine.data.rulesets
import cz.cleanship.aitools.engine.data.userDeployment
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class UserInstructionsPrinterTest {

    private val printer = UserInstructionsPrinter()

    @Test
    fun `should print the manifest as the header and the rules of every ruleset it deploys below it`() {
        // given
        val output = StringOutput()

        // when
        output.use {
            printer.print(UserInstructionsContext(userDeployment, rulesets), it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(
            """
            # globals

            My global AI tool setup

            ## Rules

            - Rule number one.
            - Rule number two.
            - Rule number three.

            """.trimIndent(),
        )
    }

    @Test
    fun `should print the rules of every ruleset of the deployment`() {
        // given
        val second = RulesetManifest(
            id = "second-ruleset",
            description = "Another ruleset",
            rules = listOf("A rule of the second ruleset."),
            metadata = ManifestMetadata(version = Version("1.0.0")),
        )
        val output = StringOutput()

        // when
        output.use {
            printer.print(UserInstructionsContext(userDeployment, mapOf(ruleset.id to ruleset, second.id to second)), it)
        }

        // then
        assertThat(output.getContent())
            .contains("- Rule number one.")
            .contains("- A rule of the second ruleset.")
    }

    @Test
    fun `should print the header alone when the deployment selects no ruleset`() {
        // given
        // - a deployment may carry only skills or agents, and then the instructions file is the header and nothing else
        val output = StringOutput()

        // when
        output.use {
            printer.print(UserInstructionsContext(userDeployment, emptyMap()), it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(
            """
            # globals

            My global AI tool setup

            """.trimIndent(),
        )
    }
}
