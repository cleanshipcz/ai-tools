package cz.cleanship.aitools.engine.tools.adapters.github

import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InputPlaceholderOutputTest {

    private val target = StringOutput()

    @Test
    fun `should rewrite a declared placeholder into a VS Code input`() {
        // given
        val output = InputPlaceholderOutput(target, listOf(variable("topic", "The topic to write about")))

        // when
        output.use {
            it.appendText("Write about {{topic}}.")
        }

        // then
        assertThat(target.getContent()).isEqualTo("Write about \${input:topic:The topic to write about}.\n")
    }

    @Test
    fun `should collapse newlines in the placeholder description`() {
        // given
        val output = InputPlaceholderOutput(target, listOf(variable("topic", "Multiline\ndescription")))

        // when
        output.use {
            it.appendText("{{topic}}")
        }

        // then
        assertThat(target.getContent()).isEqualTo("\${input:topic:Multiline description}\n")
    }

    @Test
    fun `should leave undeclared placeholders untouched`() {
        // given
        val output = InputPlaceholderOutput(target, listOf(variable("topic", "The topic")))

        // when
        output.use {
            it.appendText("{{topic}} but not {{other}}")
        }

        // then
        assertThat(target.getContent()).isEqualTo("\${input:topic:The topic} but not {{other}}\n")
    }

    @Test
    fun `should rewrite placeholders written through composed topics`() {
        // given
        val output = InputPlaceholderOutput(target, listOf(variable("topic", "The topic")))

        // when
        output.use {
            it.appendTextTopic("# {{topic}}", "Body of {{topic}}")
            it.appendListTopic("## Items", listOf("Item {{topic}}"))
        }

        // then
        assertThat(target.getContent()).isEqualTo(
            "# \${input:topic:The topic}\n\nBody of \${input:topic:The topic}\n\n" +
                "## Items\n\n- Item \${input:topic:The topic}\n\n",
        )
    }

    @Test
    fun `should pass text through unchanged when no variables are declared`() {
        // given
        val output = InputPlaceholderOutput(target, emptyList())

        // when
        output.use {
            it.appendText("Nothing to rewrite in {{topic}}")
        }

        // then
        assertThat(target.getContent()).isEqualTo("Nothing to rewrite in {{topic}}\n")
    }

    private fun variable(name: String, description: String) =
        PromptVariable(name = name, required = true, description = description)
}
