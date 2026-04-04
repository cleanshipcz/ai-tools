package cz.cleanship.aitools.server.services

import cz.cleanship.aitools.engine.models.PromptManifest

class PromptService(
    private val prompts: Map<String, PromptManifest>,
) {
    fun listPrompts(): List<PromptSummary> = prompts.values.map { it.toSummary() }

    fun getPrompt(id: String): PromptDetail? = prompts[id]?.toDetail()

    fun fillPrompt(id: String, variables: Map<String, String>): String? {
        val prompt = prompts[id] ?: return null
        return fill(prompt.content, variables)
    }

    internal fun fill(content: String, variables: Map<String, String>): String {
        var result = content
        for ((key, value) in variables) {
            result = result.replace("{{$key}}", value)
            val escapedKey = Regex.escape(key)
            val blockRegex = Regex("\\{\\{#$escapedKey}}(.*?)\\{\\{/$escapedKey}}", RegexOption.DOT_MATCHES_ALL)
            result = if (value.isNotBlank()) {
                blockRegex.replace(result) { it.groupValues[1] }
            } else {
                blockRegex.replace(result, "")
            }
        }
        val orphanedBlocks = Regex("\\{\\{#(\\w+)}}.*?\\{\\{/\\1}}", RegexOption.DOT_MATCHES_ALL)
        result = orphanedBlocks.replace(result, "")
        return result
    }

    private fun PromptManifest.toSummary() = PromptSummary(
        id = id,
        description = description,
        tags = metadata.tags.toList(),
        variables = variables.map { VariableSummary(it.name, it.required, it.description) },
    )

    private fun PromptManifest.toDetail() = PromptDetail(
        id = id,
        description = description,
        tags = metadata.tags.toList(),
        variables = variables.map { VariableSummary(it.name, it.required, it.description) },
        rules = rules,
        content = content,
        outputFormat = outputs?.format,
        version = metadata.version.toString(),
    )
}
