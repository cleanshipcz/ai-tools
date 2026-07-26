package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.Project
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.services.FilterService
import cz.cleanship.aitools.engine.services.LoaderService
import cz.cleanship.aitools.engine.services.SkillFileResolvingException
import cz.cleanship.aitools.engine.tools.AgentContext
import cz.cleanship.aitools.engine.tools.FeatureContext
import cz.cleanship.aitools.engine.tools.FragmentResolvingException
import cz.cleanship.aitools.engine.tools.GlobalContext
import cz.cleanship.aitools.engine.tools.PromptContext
import cz.cleanship.aitools.engine.tools.RulesetResolvingException
import cz.cleanship.aitools.engine.tools.SkillContext
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

    /**
     * Loads every manifest in [locations] and exports each project through every configured adapter.
     *
     * Failure policy: COLLECT-ALL-THEN-FAIL. An unresolvable ruleset or fragment reference fails only the single
     * manifest that carries it; every remaining manifest, adapter and project is still exported, and all failures
     * are reported together in one [ExportFailedException] at the very end. A manifest author therefore sees every
     * broken reference in a single run instead of rediscovering them one at a time. Because [ExportService] writes
     * atomically, no half-written artifact is left behind by a manifest that failed.
     *
     * @throws ExportFailedException if at least one manifest could not be exported
     */
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
                "Loaded {} agents, {} prompts, {} rulesets, {} fragments, {} skills, {} projects",
                allData.agents.size,
                allData.prompts.size,
                allData.rulesets.size,
                allData.fragments.size,
                allData.skills.size,
                allData.projects.size,
            )

            val failures = mutableListOf<ExportFailure>()
            for (projectManifest in allData.projects.values) {
                LOG.info("Processing project {}", projectManifest.id)
                val projectFeatures = allData.features[projectManifest] ?: emptyMap()
                val filteredSkills = filterService
                    .filter(allData.skills.values, projectManifest.deploy.skills.filter)
                    .associateBy { it.id }
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
                    fragments = filterService
                        .filter(allData.fragments.values, projectManifest.deploy.fragments.filter)
                        .associateBy { it.id },
                    skills = filteredSkills,
                    skillSourceDirs = allData.skillSourceDirs.filterKeys { it in filteredSkills },
                )

                val destination = File(project.manifest.deploy.directory).absoluteFile
                for (adapter in tools) {
                    failures += exportAdapter(project, adapter, destination, allData.rulesets, allData.fragments)
                }
                LOG.info("Processing project {} completed", project.manifest.id)
            }

            if (failures.isNotEmpty()) {
                throw ExportFailedException(failures)
            }
        }
    }

    /**
     * Exports every manifest of [project] through [adapter], isolating each manifest so that one broken reference
     * cannot skip the manifests behind it.
     *
     * @return the failures collected while exporting, empty when everything was exported
     */
    private fun exportAdapter(
        project: Project,
        adapter: ToolAdapter,
        destination: File,
        allRulesets: Map<String, RulesetManifest>,
        allFragments: Map<String, FragmentManifest>,
    ): List<ExportFailure> {
        LOG.info("{}: Exporting via adapter {}", project.manifest.id, adapter.toolType)
        if (project.manifest.deploy.replace) {
            LOG.warn("{}: Replacing existing agentic files in {}.", project.manifest.id, destination)
        }
        adapter.prepare(destination, project.manifest)

        val exports = buildList<Pair<String, () -> Unit>> {
            add("project '${project.manifest.id}'" to { adapter.export(destination, GlobalContext(project.manifest)) })
            project.agents.values.forEach { agent ->
                add(
                    "agent '${agent.id}'" to {
                        adapter.export(destination, AgentContext(agent, project.rulesets, allRulesets, project.fragments, allFragments))
                    },
                )
            }
            project.prompts.values.forEach { prompt ->
                add(
                    "prompt '${prompt.id}'" to {
                        adapter.export(destination, PromptContext(prompt, project.rulesets, allRulesets, project.fragments, allFragments))
                    },
                )
            }
            project.features.values.forEach { feature ->
                add("feature '${feature.id}'" to { adapter.export(destination, FeatureContext(feature)) })
            }
            project.skills.values.forEach { skill ->
                add(
                    "skill '${skill.id}'" to {
                        adapter.export(
                            destination,
                            SkillContext(
                                skill,
                                project.rulesets,
                                allRulesets,
                                project.fragments,
                                allFragments,
                                sourceDir = project.skillSourceDirs[skill.id],
                            ),
                        )
                    },
                )
            }
        }

        return exports.mapNotNull { (manifest, export) ->
            exportOrCollectFailure(project.manifest.id, adapter.toolType, manifest, export)
        }
    }

    /**
     * Runs a single [export], turning an authoring error into a reportable [ExportFailure] instead of letting it
     * abort the remaining exports.
     *
     * Only the deliberately named exceptions a manifest author causes are collected: an unresolvable ruleset or
     * fragment reference, and a companion file a skill manifest declares but does not ship. Everything else -
     * a programming fault, an out-of-memory error or a permission problem on the output directory - is not an
     * authoring error, so it is left to propagate and abort the run loudly instead of being reported as one more
     * broken manifest.
     *
     * @return the failure that stopped this manifest, or `null` when it was exported successfully
     */
    private fun exportOrCollectFailure(
        projectId: String,
        toolType: ToolType,
        manifest: String,
        export: () -> Unit,
    ): ExportFailure? = try {
        export()
        null
    } catch (ex: RulesetResolvingException) {
        LOG.error("{}: {} could not be exported for {}", projectId, manifest, toolType, ex)
        ExportFailure(projectId, toolType, manifest, ex)
    } catch (ex: FragmentResolvingException) {
        LOG.error("{}: {} could not be exported for {}", projectId, manifest, toolType, ex)
        ExportFailure(projectId, toolType, manifest, ex)
    } catch (ex: SkillFileResolvingException) {
        LOG.error("{}: {} could not be exported for {}", projectId, manifest, toolType, ex)
        ExportFailure(projectId, toolType, manifest, ex)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ToolsEngine::class.java)
    }
}

/**
 * A single manifest that could not be exported, together with the resolution failure that stopped it.
 */
data class ExportFailure(
    val projectId: String,
    val toolType: ToolType,
    val manifest: String,
    val cause: RuntimeException,
)

/**
 * Thrown by [ToolsEngine.process] once every project has been processed, when at least one manifest failed to
 * export. Carries the original resolver messages so the caller can report every broken reference at once.
 */
class ExportFailedException(
    val failures: List<ExportFailure>,
) : RuntimeException(
        buildString {
            // A broken manifest fails once per adapter, so counting the failures would report a single broken
            // agent as six problems with the six tools of config.yml. The count is therefore over the distinct
            // manifests, while the body still lists every adapter that could not export them.
            val brokenManifests = failures.distinctBy { it.projectId to it.manifest }.size
            append("Export failed for $brokenManifests manifest(s):")
            failures.forEach { failure ->
                append("\n  - [${failure.projectId} | ${failure.toolType} | ${failure.manifest}] ${failure.cause.message}")
            }
        },
    )
