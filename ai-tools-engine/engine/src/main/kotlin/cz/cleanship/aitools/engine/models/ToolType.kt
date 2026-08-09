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

/**
 * The spelling a manifest writes this tool as - the value `@SerialName` decodes - so that a message echoing back
 * something an author typed can use their own word rather than the Kotlin constant. It is read from the serializer
 * instead of repeated in a second table, which could drift away from the annotations above.
 */
val ToolType.serialName: String
    get() = ToolType.serializer().descriptor.getElementName(ordinal)
