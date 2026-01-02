package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.Project
import cz.cleanship.aitools.engine.services.FilterService
import cz.cleanship.aitools.engine.services.LoaderService
import cz.cleanship.aitools.engine.services.Locations
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.adapters.antigravity.AntigravityAdapter
import cz.cleanship.aitools.engine.tools.adapters.github.GitHubCopilotAdapter
import cz.cleanship.aitools.engine.tools.adapters.windsurf.WindsurfAdapter
import org.slf4j.LoggerFactory
import java.io.File

class ToolsEngine(
    private val loaderService: LoaderService = LoaderService(),
    private val filterService: FilterService = FilterService(),
    private val tools: List<ToolAdapter> = listOf(
        WindsurfAdapter(),
        AntigravityAdapter(),
        GitHubCopilotAdapter(),
    ),
) {

    fun process(
        locations: Locations
    ) {
        LOG.info("Processing locations {}", locations)
        val allData = loaderService.loadAll(locations)
        LOG.info("Loaded {} agents, {} prompts, {} rulesets, {} projects", allData.agents.size, allData.prompts.size, allData.rulesets.size, allData.projects.size)

        for (projectManifest in allData.projects.values) {
            LOG.info("Processing project {}", projectManifest.id)
            val project = Project(
                projectManifest,
                features = filterService.filter((allData.features[projectManifest] ?: emptyMap()).values, projectManifest.deploy.features.filter).associateBy { it.id },
                agents = filterService.filter(allData.agents.values, projectManifest.deploy.agents.filter).associateBy { it.id },
                prompts = filterService.filter(allData.prompts.values, projectManifest.deploy.prompts.filter).associateBy { it.id },
                rulesets = filterService.filter(allData.rulesets.values, projectManifest.deploy.rulesets.filter).associateBy { it.id },
            )

            val destination = File(project.manifest.deploy.directory).absoluteFile
            for (adapter in tools) {
                project.agents.values.forEach {
                    adapter.export(destination, AgentContext(it, project.rulesets))
                }
                project.prompts.values.forEach {
                    adapter.export(destination, PromptContext(it, project.rulesets))
                }
                project.features.values.forEach {
                    adapter.export(destination, FeatureContext(it))
                }
            }
            LOG.info("Processing project {} completed", project.manifest.id)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ToolsEngine::class.java)
    }
}