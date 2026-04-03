package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.expectedFragment
import cz.cleanship.aitools.engine.data.fragment
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FragmentPrinterTest {

    @Test
    fun `should print fragment manifest`() {
        // given
        val output = StringOutput()

        // when
        output.use {
            FragmentPrinter().print(fragment, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedFragment)
    }
}
