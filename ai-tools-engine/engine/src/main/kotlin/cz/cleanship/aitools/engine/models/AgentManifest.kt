package cz.cleanship.aitools.engine.models

import kotlinx.serialization.Serializable

@Serializable
data class AgentManifest(
    override val id: String,
    override val description: String,
    val rulesets: List<String> = emptyList(),
    val rules: List<String> = emptyList(),
    val persona: String,
    val prompt: String,
    val constraints: List<String> = emptyList(),
    override val metadata: ManifestMetadata,
) : VersionedManifest
