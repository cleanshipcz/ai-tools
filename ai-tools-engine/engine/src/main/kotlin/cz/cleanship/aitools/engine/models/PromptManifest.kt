package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class PromptManifest(
    override val id: String,
    override val version: Version,
    val description: String,
    val tags: List<String> = emptyList(),
    val variables: List<PromptVariable> = emptyList(),
    val rules: List<String> = emptyList(),
    val content: String,
    val outputs: PromptOutput? = null,
    override val metadata: ManifestMetadata = ManifestMetadata(),
) : VersionedManifest

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
