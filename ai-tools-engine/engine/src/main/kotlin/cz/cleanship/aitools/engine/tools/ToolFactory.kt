package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.tools.adapters.antigravity.AntigravityAdapter
import cz.cleanship.aitools.engine.tools.adapters.claude.ClaudeAdapter
import cz.cleanship.aitools.engine.tools.adapters.codex.CodexAdapter
import cz.cleanship.aitools.engine.tools.adapters.cursor.CursorAdapter
import cz.cleanship.aitools.engine.tools.adapters.github.GitHubCopilotAdapter
import cz.cleanship.aitools.engine.tools.adapters.windsurf.WindsurfAdapter

object ToolFactory {
    fun create(type: ToolType): ToolAdapter = when (type) {
        ToolType.WINDSURF -> WindsurfAdapter()
        ToolType.ANTIGRAVITY -> AntigravityAdapter()
        ToolType.GITHUB_COPILOT -> GitHubCopilotAdapter()
        ToolType.CURSOR -> CursorAdapter()
        ToolType.CLAUDE -> ClaudeAdapter()
        ToolType.CODEX -> CodexAdapter()
    }
}
