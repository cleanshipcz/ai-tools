package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.commandSkill
import cz.cleanship.aitools.engine.data.expectedCommandSkill
import cz.cleanship.aitools.engine.data.expectedMcpToolSkill
import cz.cleanship.aitools.engine.data.mcpToolSkill
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SkillPrinterTest {

    @Test
    fun `should print command-based skill manifest`() {
        // given
        val context = SkillContext(commandSkill)
        val output = StringOutput()

        // when
        output.use {
            SkillPrinter().print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedCommandSkill)
    }

    @Test
    fun `should print mcp-tool-based skill manifest`() {
        // given
        val context = SkillContext(mcpToolSkill)
        val output = StringOutput()

        // when
        output.use {
            SkillPrinter().print(context, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedMcpToolSkill)
    }
}
