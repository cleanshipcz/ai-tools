package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.FeatureManifest

class FeaturePrinter : Printer<FeatureContext> {
    override fun print(entity: FeatureContext, output: Output): Output {
        val feature = entity.feature

        output.appendTextTopic("# ${feature.id}", feature.description)

        output.appendLine("## Context")
        output.appendLine()

        output.appendTextTopic("### Overview", feature.context?.overview)
        output.appendTextTopic("### Architecture", feature.context?.architecture)
        output.appendListTopic("### Dependencies", feature.context?.dependencies)
        output.appendListTopic("### Files", feature.context?.files)

        output.appendTextTopic("## Prompt", feature.prompt)

        output.appendListTopic("## Acceptance Criteria", feature.acceptanceCriteria)

        output.appendListTopic("## Constraints", feature.constraints)

        return output
    }
}

data class FeatureContext(
    val feature: FeatureManifest,
)
