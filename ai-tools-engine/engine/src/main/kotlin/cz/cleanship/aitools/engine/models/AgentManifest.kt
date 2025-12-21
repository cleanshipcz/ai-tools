package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class AgentManifest(
    override val id: String,
    override val description: String,
    val rulepacks: List<String> = emptyList(),
    val persona: String,
    val prompt: String,
    val constraints: List<String> = emptyList(),
    override val metadata: ManifestMetadata,
) : VersionedManifest

@Serializable
data class AgentDefaults(
    val temperature: Double = 0.3,
    val style: String = "technical",
)

@Serializable
data class AgentPrompt(
    val system: String,
    val userTemplate: String? = null,
)
