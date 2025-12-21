package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class AgentManifest(
    val id: String,
    val version: String = "1.0.0",
    val purpose: String,
    val description: String? = null,
    val rulepacks: List<String> = emptyList(),
    val capabilities: List<String> = emptyList(),
    val defaults: AgentDefaults = AgentDefaults(),
    val prompt: AgentPrompt,
    val tools: List<String> = emptyList(),
    val constraints: List<String> = emptyList(),
    val metadata: ManifestMetadata = ManifestMetadata(),
)

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
