package cz.cleanship.aitools.server.services

import kotlinx.serialization.Serializable

@Serializable
data class PromptSummary(
    val id: String,
    val description: String,
    val tags: List<String>,
    val variables: List<VariableSummary>,
)

@Serializable
data class PromptDetail(
    val id: String,
    val description: String,
    val tags: List<String>,
    val variables: List<VariableSummary>,
    val rules: List<String>,
    val content: String,
    val outputFormat: String?,
    val version: String,
)

@Serializable
data class VariableSummary(
    val name: String,
    val required: Boolean,
    val description: String,
)

@Serializable
data class FillRequest(
    val variables: Map<String, String>,
)

@Serializable
data class FillResponse(
    val content: String,
)
