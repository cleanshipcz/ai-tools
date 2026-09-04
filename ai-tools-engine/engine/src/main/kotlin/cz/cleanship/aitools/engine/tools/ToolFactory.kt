package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.services.DryRunArtifactSink
import cz.cleanship.aitools.engine.services.ExportService
import cz.cleanship.aitools.engine.services.FileSystemArtifactSink
import cz.cleanship.aitools.engine.tools.adapters.antigravity.AntigravityAdapter
import cz.cleanship.aitools.engine.tools.adapters.claude.ClaudeAdapter
import cz.cleanship.aitools.engine.tools.adapters.codex.CodexAdapter
import cz.cleanship.aitools.engine.tools.adapters.cursor.CursorAdapter
import cz.cleanship.aitools.engine.tools.adapters.github.GitHubCopilotAdapter
import cz.cleanship.aitools.engine.tools.adapters.windsurf.WindsurfAdapter

object ToolFactory {

    /**
     * Builds the adapter of [type].
     *
     * @param dryRun whether the adapter writes nothing and only reports what it would write. It is decided here for
     * every tool at once, so that a tool honouring a dry run is never something an individual adapter has to
     * remember to do - see [cz.cleanship.aitools.engine.services.ArtifactSink].
     */
    fun create(type: ToolType, dryRun: Boolean = false): ToolAdapter {
        val exportService = ExportService(if (dryRun) DryRunArtifactSink else FileSystemArtifactSink)
        return when (type) {
            ToolType.WINDSURF -> WindsurfAdapter(exportService = exportService)
            ToolType.ANTIGRAVITY -> AntigravityAdapter(exportService = exportService)
            ToolType.GITHUB_COPILOT -> GitHubCopilotAdapter(exportService = exportService)
            ToolType.CURSOR -> CursorAdapter(exportService = exportService)
            ToolType.CLAUDE -> ClaudeAdapter(exportService = exportService)
            ToolType.CODEX -> CodexAdapter(exportService = exportService)
        }
    }
}
