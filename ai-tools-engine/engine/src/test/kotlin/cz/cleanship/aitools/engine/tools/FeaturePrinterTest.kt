package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.data.expectedFeature
import cz.cleanship.aitools.engine.data.feature
import cz.cleanship.aitools.engine.utils.StringOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FeaturePrinterTest {


    @Test
    fun `should print feature manifest`() {
        // given
        val feature = FeatureContext(feature)
        val output = StringOutput()

        // when
        output.use {
            FeaturePrinter().print(feature, it)
        }

        // then
        assertThat(output.getContent().trimIndent()).isEqualTo(expectedFeature)
    }
}
