package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class AgentManifest(
    override val id: String,
    override val version: Version,
    val purpose: String,
    val description: String? = null,
    val rulepacks: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val defaults: AgentDefaults = AgentDefaults(),
    val prompt: AgentPrompt,
    val tools: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    override val metadata: ManifestMetadata = ManifestMetadata(),
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
