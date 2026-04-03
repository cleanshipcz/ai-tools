package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SkillManifest(
    override val id: String,
    override val description: String,
    override val metadata: ManifestMetadata,
    val command: SkillCommand? = null,
    @SerialName("mcp_tool")
    val mcpTool: String? = null,
    val fragments: List<String> = emptyList(),
    val inputs: List<SkillInput> = emptyList(),
    val triggers: List<String> = emptyList(),
    val prerequisites: List<String> = emptyList(),
    val instructions: String? = null,
    @SerialName("timeout_sec")
    val timeoutSec: Int? = null,
    val outputs: SkillOutputs? = null,
) : VersionedManifest {
    init {
        require(command != null || mcpTool != null) {
            "Skill '$id' must define either 'command' or 'mcp_tool'."
        }
        require(command == null || mcpTool == null) {
            "Skill '$id' must define either 'command' or 'mcp_tool', not both."
        }
    }
}

@Serializable
data class SkillCommand(
    val program: String,
    val args: List<String> = emptyList(),
    val cwd: String? = null,
    val env: Map<String, String> = emptyMap(),
)

@Serializable
data class SkillInput(
    val name: String,
    val type: String,
    val required: Boolean = false,
    val description: String? = null,
)

@Serializable
data class SkillOutputs(
    val files: List<SkillOutputFile> = emptyList(),
)

@Serializable
data class SkillOutputFile(
    val path: String,
    val description: String? = null,
)
