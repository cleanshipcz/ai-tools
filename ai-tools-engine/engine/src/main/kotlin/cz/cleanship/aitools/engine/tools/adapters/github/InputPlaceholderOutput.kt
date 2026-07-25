package cz.cleanship.aitools.engine.tools.adapters.github

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.PromptVariable

/**
 * Rewrites `{{variable}}` placeholders into the `${input:variable:description}` form VS Code understands, so
 * that running a generated `.prompt.md` file actually asks the user for the value.
 *
 * Only variables declared by the prompt manifest are rewritten, any other `{{...}}` is forwarded untouched.
 * The rewrite lives here instead of in the shared prompt printer because it is GitHub Copilot specific - the
 * other tools consume the same printer and must keep seeing the `{{variable}}` form.
 */
internal class InputPlaceholderOutput(
    private val delegate: Output,
    variables: List<PromptVariable>,
) : Output {

    private val replacements: Map<String, String> = variables.associate { variable ->
        "{{${variable.name}}}" to "\${input:${variable.name}:${variable.description.replace("\n", " ")}}"
    }

    override fun appendText(text: String) = delegate.appendText(rewrite(text))

    override fun appendLine(line: String) = delegate.appendLine(rewrite(line))

    override fun close() = delegate.close()

    private fun rewrite(text: String) = replacements.entries.fold(text) { rewritten, (placeholder, input) ->
        rewritten.replace(placeholder, input)
    }
}
