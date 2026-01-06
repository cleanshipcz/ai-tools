package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.ProjectManifest

class GlobalFilePrinter : Printer<GlobalContext> {

    override fun print(entity: GlobalContext, output: Output): Output {
        val project = entity.project
        output.appendTextTopic("# ${project.id}", project.description)

        output.appendTextTopic(
            "## Overview",
            project.context.overview,
        )

        output.appendListTopic(
            "## Rules",
            project.context.rules,
        )

        output.appendListTopic(
            "## Documentation files",
            (
                project.context.documentation.readme
                    ?.let { listOf(it) } ?: emptyList()
            ) + project.context.documentation.additional.map {
                "${it.path} - (${it.description})"
            },
        )

        return output
    }
}

data class GlobalContext(
    val project: ProjectManifest,
)
