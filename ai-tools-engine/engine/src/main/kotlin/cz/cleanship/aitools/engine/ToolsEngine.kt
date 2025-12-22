package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.AllManifests
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.services.LoaderService
import cz.cleanship.aitools.engine.services.Locations
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.adapters.windsurf.WindsurfAdapter

class ToolsEngine(
    private val loaderService: LoaderService = LoaderService(),
    private val tools: List<ToolAdapter> = listOf(
        WindsurfAdapter(),
    )
) {

    fun process(
        locations: Locations,
        project: ProjectManifest
    ) {
        val allData = loaderService.loadAll(locations)
        val destination = project.directory
        for (adapter in tools) {
            allData.agents.values.forEach {
                adapter.export(destination, AgentContext(it, allData.rulepacks))
            }
            allData.prompts.values.forEach {
                adapter.export(destination, PromptContext(it))
            }
            allData.features.values.forEach {
                adapter.export(destination, FeatureContext(it))
            }
        }
    }
}