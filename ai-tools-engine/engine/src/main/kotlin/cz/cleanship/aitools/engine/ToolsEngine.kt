package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.services.FilterService
import cz.cleanship.aitools.engine.services.LoaderService
import cz.cleanship.aitools.engine.services.Locations
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.adapters.antigravity.AntigravityAdapter
import cz.cleanship.aitools.engine.tools.adapters.windsurf.WindsurfAdapter
import org.slf4j.LoggerFactory
import java.io.File

class ToolsEngine(
    private val loaderService: LoaderService = LoaderService(),
    private val filterService: FilterService = FilterService(),
    private val tools: List<ToolAdapter> = listOf(
        WindsurfAdapter(),
        AntigravityAdapter(),
    ),
) {

    fun process(
        locations: Locations,
        project: ProjectManifest
    ) {
        LOG.info("Processing project {}", project.id)
        val allData = loaderService.loadAll(locations)
        LOG.info("Loaded {} agents, {} prompts, {} features, {} rulesets", allData.agents.size, allData.prompts.size, allData.features.size, allData.rulesets.size)
        val destination = File(project.directory)
        for (adapter in tools) {
            allData.agents.values.filter(filterService.createFilter(project.agents.filter)).forEach {
                adapter.export(destination, AgentContext(it, allData.rulesets))
            }
            allData.prompts.values.filter(filterService.createFilter(project.prompts.filter)).forEach {
                adapter.export(destination, PromptContext(it, allData.rulesets))
            }
            allData.features.values.filter(filterService.createFilter(project.features.filter)).forEach {
                adapter.export(destination, FeatureContext(it))
            }
        }
        LOG.info("Processing project {} completed", project.id)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ToolsEngine::class.java)
    }
}