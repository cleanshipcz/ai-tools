package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class PromptManifest(
    val id: String,
    val version: String = "1.0.0",
    val description: String,
    val tags: List<String> = emptyList(),
    val variables: List<PromptVariable> = emptyList(),
    val rules: List<String> = emptyList(),
    val content: String,
    val outputs: PromptOutput? = null,
    val metadata: ManifestMetadata = ManifestMetadata(),
)

@Serializable
data class PromptVariable(
    val name: String,
    val required: Boolean = true,
    val description: String,
)

@Serializable
data class PromptOutput(
    val format: String? = null,
    val examples: List<String> = emptyList(),
)
