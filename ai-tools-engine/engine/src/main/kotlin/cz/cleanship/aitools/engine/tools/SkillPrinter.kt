package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.io.Output
import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.SkillManifest

class SkillPrinter(
    private val fragmentResolver: FragmentResolver = FragmentResolver(),
) : Printer<SkillContext> {
    override fun print(entity: SkillContext, output: Output): Output {
        val skill = entity.skill

        output.appendTextTopic("# ${skill.id}", skill.description)

        output.appendListTopic("## When to use", skill.triggers)

        output.appendListTopic("## Prerequisites", skill.prerequisites)

        output.appendLine("## How to use")
        output.appendLine()

        if (skill.command != null) {
            printCommand(skill, output)
        } else if (skill.mcpTool != null) {
            printMcpTool(skill, output)
        }

        if (skill.timeoutSec != null) {
            output.appendLine("**Timeout:** ${skill.timeoutSec} seconds")
            output.appendLine()
        }

        if (skill.outputs != null && skill.outputs.files.isNotEmpty()) {
            output.appendLine("## Output files")
            output.appendLine()
            for (file in skill.outputs.files) {
                if (file.description != null) {
                    output.appendLine("- `${file.path}`: ${file.description}")
                } else {
                    output.appendLine("- `${file.path}`")
                }
            }
            output.appendLine()
        }

        output.appendTextTopic("## Instructions", skill.instructions)

        val matchedFragments = fragmentResolver.resolve(
            patterns = skill.fragments,
            available = entity.fragments,
            requestedBy = "skill '${skill.id}'",
            allFragments = entity.allFragments,
        )
        if (matchedFragments.isNotEmpty()) {
            output.appendLine("## Fragments")
            output.appendLine()
            for (fragment in matchedFragments) {
                output.appendTextTopic("### ${fragment.id}", fragment.content)
            }
        }

        return output
    }

    private fun printCommand(skill: SkillManifest, output: Output) {
        val command = skill.command!!
        val fullCommand = buildString {
            append(command.program)
            if (command.args.isNotEmpty()) {
                append(" ")
                append(command.args.joinToString(" "))
            }
        }
        output.appendLine("Run the following command:")
        output.appendLine()
        output.appendLine("```bash")
        output.appendLine(fullCommand)
        output.appendLine("```")
        output.appendLine()

        if (command.cwd != null && command.cwd != ".") {
            output.appendLine("**Working directory:** `${command.cwd}`")
            output.appendLine()
        }

        if (command.env.isNotEmpty()) {
            output.appendLine("**Environment variables:**")
            output.appendLine()
            for ((key, value) in command.env) {
                output.appendLine("- `$key=$value`")
            }
            output.appendLine()
        }
    }

    private fun printMcpTool(skill: SkillManifest, output: Output) {
        output.appendLine("This skill uses the MCP tool: `${skill.mcpTool}`")
        output.appendLine()

        if (skill.inputs.isNotEmpty()) {
            output.appendLine("### Inputs")
            output.appendLine()
            for (input in skill.inputs) {
                val required = if (input.required) " (required)" else ""
                val desc = if (input.description != null) ": ${input.description}" else ""
                output.appendLine("- `${input.name}` (${input.type})$required$desc")
            }
            output.appendLine()
        }
    }
}

data class SkillContext(
    val skill: SkillManifest,
    val fragments: Map<String, FragmentManifest> = emptyMap(),
    val allFragments: Map<String, FragmentManifest> = fragments,
)
