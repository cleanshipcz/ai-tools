package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.Project
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.services.FilterService
import cz.cleanship.aitools.engine.services.LoaderService
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.RulesetResolvingException
import cz.cleanship.aitools.engine.tools.ToolAdapter
import cz.cleanship.aitools.engine.tools.adapters.antigravity.AntigravityAdapter
import cz.cleanship.aitools.engine.tools.adapters.claude.ClaudeAdapter
import cz.cleanship.aitools.engine.tools.adapters.codex.CodexAdapter
import cz.cleanship.aitools.engine.tools.adapters.cursor.CursorAdapter
import cz.cleanship.aitools.engine.tools.adapters.github.GitHubCopilotAdapter
import cz.cleanship.aitools.engine.tools.adapters.windsurf.WindsurfAdapter
import cz.cleanship.telemetry.SpanKind
import cz.cleanship.telemetry.Telemetry
import cz.cleanship.telemetry.TelemetryConfig
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

class ToolsEngine(
    private val loaderService: LoaderService = LoaderService(),
    private val filterService: FilterService = FilterService(),
    private val tools: List<ToolAdapter> = listOf(
        WindsurfAdapter(),
        AntigravityAdapter(),
        GitHubCopilotAdapter(),
        ClaudeAdapter(),
        CodexAdapter(),
        CursorAdapter(),
    ),
) {

    private val telemetry = Telemetry.create(TelemetryConfig.fromEnvironment())

    fun process(
        locations: Locations,
    ) = runBlocking {
        telemetry.inSpan(
            name = "ToolsEngine.process",
            kind = SpanKind.INTERNAL,
            attributes = mapOf(
                "locations" to locations.toString(),
            ),
        ) {
            LOG.info("Processing locations {}", locations)
            val allData = loaderService.loadAll(locations)
            LOG.info(
                "Loaded {} agents, {} prompts, {} rulesets, {} projects",
                allData.agents.size,
                allData.prompts.size,
                allData.rulesets.size,
                allData.projects.size,
            )

            for (projectManifest in allData.projects.values) {
                LOG.info("Processing project {}", projectManifest.id)
                val projectFeatures = allData.features[projectManifest] ?: emptyMap()
                val project = Project(
                    projectManifest,
                    features = filterService
                        .filter(projectFeatures.values, projectManifest.deploy.features.filter)
                        .associateBy { it.id },
                    agents = filterService
                        .filter(allData.agents.values, projectManifest.deploy.agents.filter)
                        .associateBy { it.id },
                    prompts = filterService
                        .filter(allData.prompts.values, projectManifest.deploy.prompts.filter)
                        .associateBy { it.id },
                    rulesets = filterService
                        .filter(allData.rulesets.values, projectManifest.deploy.rulesets.filter)
                        .associateBy { it.id },
                )

                val destination = File(project.manifest.deploy.directory).absoluteFile
                for (adapter in tools) {
                    exportAdapter(project, adapter, destination, allData.rulesets)
                }
                LOG.info("Processing project {} completed", project.manifest.id)
            }
        }
    }

    private fun exportAdapter(
        project: Project,
        adapter: ToolAdapter,
        destination: File,
        allRulesets: Map<String, RulesetManifest>,
    ) {
        try {
            LOG.info("{}: Processing agent {}", project.manifest.id, adapter.toolType)
            if (project.manifest.deploy.replace) {
                LOG.warn("{}: Replacing existing agentic files in {}.", project.manifest.id, destination)
            }
            adapter.prepare(destination, project.manifest)
            adapter.export(destination, GlobalContext(project.manifest))
            project.agents.values.forEach {
                adapter.export(destination, AgentContext(it, project.rulesets, allRulesets))
            }
            project.prompts.values.forEach {
                adapter.export(destination, PromptContext(it, project.rulesets, allRulesets))
            }
            project.features.values.forEach {
                adapter.export(destination, FeatureContext(it))
            }
        } catch (ex: RulesetResolvingException) {
            LOG.error("Failed to resolve rulesets for project {}", project.manifest.id, ex)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ToolsEngine::class.java)
    }
}
