package cz.cleanship.aitools.engine.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ToolType {
    @SerialName("windsurf")
    WINDSURF,

    @SerialName("antigravity")
    ANTIGRAVITY,

    @SerialName("github_copilot")
    GITHUB_COPILOT,

    @SerialName("cursor")
    CURSOR,

    @SerialName("claude")
    CLAUDE,

    @SerialName("codex")
    CODEX,
}
