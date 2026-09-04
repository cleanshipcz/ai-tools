package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.ProjectDocumentation
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

        output.appendListTopic("## Documentation files", documentationItems(project.context.documentation))

        return output
    }

    /**
     * Renders the documentation of a project as one list: the readme first, then every `per_topic` group in the
     * order the manifest declares them, then the `additional` files.
     *
     * A topic group is a single list item whose files are nested bullets below the group name. It is rendered as one
     * multi-line item on purpose: [Output.appendListTopic] indents the continuation lines of an item by two spaces,
     * which is exactly the indentation a nested Markdown bullet needs, so no second list helper is required.
     */
    private fun documentationItems(documentation: ProjectDocumentation): List<String> {
        val readme = listOfNotNull(documentation.readme)
        val topics = documentation.perTopic.map { (topic, files) ->
            files.entries.joinToString(separator = "", prefix = "$topic:") { (name, path) -> "\n- $name: $path" }
        }
        val additional = documentation.additional.map { "${it.path} - (${it.description})" }
        return readme + topics + additional
    }
}

data class GlobalContext(
    val project: ProjectManifest,
)
