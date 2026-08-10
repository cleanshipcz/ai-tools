package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.env.VariableResolver
import cz.cleanship.aitools.engine.env.VariableSubstitutionException
import cz.cleanship.aitools.engine.io.resolveDeclaredPath
import cz.cleanship.aitools.engine.models.AllManifests
import cz.cleanship.aitools.engine.models.DuplicateManifestId
import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.Project
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.UserDeployment
import cz.cleanship.aitools.engine.models.UserDeploymentManifest
import cz.cleanship.aitools.engine.models.serialName
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
import cz.cleanship.aitools.engine.tools.UserInstructionsContext
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

/**
 * @param workingDirectory the `--working-dir` of the run, the directory `config.yml` was read from. A relative
 * `deploy.directory` resolves against it - see [resolveDeclaredPath] - so it is the same base the `locations.*`
 * paths of that same `config.yml` already use. It is required rather than defaulted because the adapters delete
 * under whatever it resolves to, which is not a decision to make by omission.
 * @param variables the variables of the run, which `deploy.directory` is substituted with before it is resolved -
 * see [VariableResolver]. They are the ones the config files of the run declared, so a project manifest reads the
 * same variables the `locations.*` of that run did. The default declares none and falls back to the environment of
 * the process, which is what an engine built without a config sees.
 * @param userHome the home directory a [UserDeploymentManifest] is deployed under, from which each adapter derives
 * the per-user location of its own tool - `<home>/.claude`, `<home>/.codex`. It defaults to the home of the user
 * running the engine, which is the only home a deploy is ever meant to reach; a test overrides it so that it writes
 * into a directory of its own instead.
 */
class ToolsEngine(
    private val workingDirectory: File,
    private val loaderService: LoaderService = LoaderService(),
    private val filterService: FilterService = FilterService(),
    private val variables: VariableResolver = VariableResolver(),
    private val userHome: File = File(System.getProperty("user.home")),
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
     * Loads every manifest in [locations] and exports each project through every configured adapter, or through the
     * subset a project narrows itself down to with `deploy.tools` - see [selectAdapters]. The user deployments of the
     * run follow the projects, each into the per-user location of the tools it names - see [exportUserDeployments].
     *
     * Failure policy: COLLECT-ALL-THEN-FAIL. An unresolvable ruleset or fragment reference fails only the single
     * manifest that carries it; every remaining manifest, adapter and project is still exported, and all failures
     * are reported together in one [ExportFailedException] at the very end. A manifest author therefore sees every
     * broken reference in a single run instead of rediscovering them one at a time. Because [ExportService] writes
     * atomically, no half-written artifact is left behind by a manifest that failed.
     *
     * A project whose own id or feature ids collide is not exported at all - see [LoaderService.loadAll] - and is
     * reported through the same [ExportFailedException], so a single ambiguous id cannot stop the projects that
     * have nothing to do with it.
     *
     * @throws ExportFailedException if at least one manifest could not be exported or at least one project was
     * left out because its ids collide
     * @throws DeployDirectoryResolvingException if a `deploy.directory` references a variable that nothing declares,
     * which stops the run before a single project is exported - see [resolveDeployDirectories]
     * @throws cz.cleanship.aitools.engine.services.DuplicateManifestIdException if manifests shared by every
     * project declare the same id, which no project can be exported around
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
                "Loaded {} agents, {} prompts, {} rulesets, {} fragments, {} skills, {} projects, {} user deployments",
                allData.agents.size,
                allData.prompts.size,
                allData.rulesets.size,
                allData.fragments.size,
                allData.skills.size,
                allData.projects.size,
                allData.userDeployments.size,
            )
            allData.duplicates.forEach { LOG.error("Not exporting the deployment(s) affected by an ambiguous id. {}", it.message) }

            // A run with no adapters exports nothing at all, and every project below is skipped before it reaches a
            // log line, so the misconfiguration is named here rather than leaving the run silent and successful.
            if (tools.isEmpty()) {
                LOG.warn("This run configures no tools, so no project is exported. Declare the tools to build under 'tools:' in config.yml, or in config.local.yml, which replaces that list.")
            }

            val destinations = resolveDeployDirectories(allData.projects.values)

            val failures = mutableListOf<ExportFailure>()
            for (projectManifest in allData.projects.values) {
                // Selecting before the project is assembled keeps a project that exports through no tool out of the
                // log entirely, rather than bracketing it in the lines that report a deploy which never happened.
                val adapters = selectAdapters(projectManifest.id, projectManifest.deploy.tools, toolsField = "deploy.tools", subject = "project")
                if (adapters.isEmpty()) continue
                LOG.info("Processing project {}", projectManifest.id)
                val project = assembleProject(projectManifest, allData)
                val destination = destinations.getValue(projectManifest.id)
                for (adapter in adapters) {
                    failures += exportAdapter(project, adapter, destination, allData.rulesets, allData.fragments)
                }
                LOG.info("Processing project {} completed", projectManifest.id)
            }

            failures += exportUserDeployments(allData)

            if (failures.isNotEmpty() || allData.duplicates.isNotEmpty()) {
                throw ExportFailedException(failures, allData.duplicates)
            }
        }
    }

    /**
     * Resolves the directory every manifest of [projects] deploys to: the variables of the run are expanded first -
     * see [VariableResolver] - and what they produced is resolved by [resolveDeclaredPath] afterwards, so a variable
     * is free to supply the absolute base a relative value would otherwise be denied.
     *
     * Every project is resolved before any project is exported, and one failure stops the whole run. A deploy may
     * delete the directories it generates before writing them again, so a run that cannot finish must not have
     * already replaced the projects that happened to be read first - which is why this cannot sit in the export loop,
     * where the order the manifests were found in would decide how much of the run had happened. It also reaches the
     * projects that export through no adapter at all, which that loop skips before it would look at their directory.
     *
     * The run stops rather than collecting the failure like the authoring errors of [exportOrCollectFailure], because
     * a variable nothing declares is a fault in the configuration of the run rather than in one manifest. Every such
     * failure is still gathered first, so an author fixing their variables is told about all of them at once.
     *
     * @return the directory each project deploys to, by project id
     * @throws DeployDirectoryResolvingException if at least one declared directory could not be resolved
     */
    private fun resolveDeployDirectories(projects: Collection<ProjectManifest>): Map<String, File> {
        val destinations = mutableMapOf<String, File>()
        val failures = mutableListOf<VariableSubstitutionException>()
        for (manifest in projects) {
            try {
                val declared = variables.substitute(manifest.deploy.directory, origin = "deploy.directory of project '${manifest.id}'")
                destinations[manifest.id] = workingDirectory.resolveDeclaredPath(declared)
            } catch (ex: VariableSubstitutionException) {
                LOG.error("{}: deploy.directory could not be resolved. {}", manifest.id, ex.message)
                failures += ex
            }
        }
        if (failures.isNotEmpty()) {
            throw DeployDirectoryResolvingException(failures)
        }
        return destinations
    }

    /**
     * Builds the project that is exported: every manifest of [allData] that survives the filter [projectManifest]
     * declares for its kind, indexed by id.
     */
    private fun assembleProject(projectManifest: ProjectManifest, allData: AllManifests): Project {
        val projectFeatures = allData.features[projectManifest] ?: emptyMap()
        val filteredSkills = filterService
            .filter(allData.skills.values, projectManifest.deploy.skills.filter)
            .associateBy { it.id }
        return Project(
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
    }

    /**
     * Exports every user deployment of [allData] into [userHome], through the adapters its `tools` list selects.
     *
     * It mirrors the project loop above, minus the parts a user scope does not have: there is no directory to resolve
     * - each adapter knows the per-user location of its own tool - and no features to deploy. A tool the engine has
     * no user-scope layout for is reported rather than passed over, so a manifest naming it is never dropped without
     * a word - see [ToolAdapter.userScope].
     *
     * @return the failures collected while exporting, which the caller reports together with those of the projects
     */
    private fun exportUserDeployments(allData: AllManifests): List<ExportFailure> {
        val failures = mutableListOf<ExportFailure>()
        for (manifest in allData.userDeployments.values) {
            val adapters = selectAdapters(manifest.id, manifest.tools, toolsField = "tools", subject = "user deployment")
            if (adapters.isEmpty()) continue
            LOG.info("Processing user deployment {}", manifest.id)
            val deployment = assembleUserDeployment(manifest, allData)
            for (adapter in adapters) {
                failures += exportUserAdapter(deployment, adapter, allData.rulesets, allData.fragments)
            }
            LOG.info("Processing user deployment {} completed", manifest.id)
        }
        return failures
    }

    /**
     * Exports every manifest of [deployment] into the user scope of [adapter], isolating each of them the way
     * [exportAdapter] isolates the manifests of a project.
     *
     * @return the failures collected while exporting, empty when everything was exported or when this tool has no
     * user scope at all
     */
    private fun exportUserAdapter(
        deployment: UserDeployment,
        adapter: ToolAdapter,
        allRulesets: Map<String, RulesetManifest>,
        allFragments: Map<String, FragmentManifest>,
    ): List<ExportFailure> {
        val manifest = deployment.manifest
        val exporter = adapter.userScope(userHome, manifest)
        if (exporter == null) {
            LOG.warn("{}: {} has no user-scope layout in this engine, so the manifest is not deployed for it.", manifest.id, adapter.toolType)
            return emptyList()
        }
        LOG.info("{}: Deploying into the user scope of {} under '{}'", manifest.id, adapter.toolType, userHome)
        if (manifest.replace) {
            // Quoted for the same reason the project loop quotes its destination: a home can end in a character
            // that reads as part of the sentence around it.
            LOG.warn("{}: Replacing the artifacts of this deployment under '{}'.", manifest.id, userHome)
        }

        val exports = buildList<Pair<String, () -> Unit>> {
            add(
                "instructions of '${manifest.id}'" to {
                    exporter.export(UserInstructionsContext(manifest, deployment.rulesets))
                },
            )
            deployment.agents.values.forEach { agent ->
                add(
                    "agent '${agent.id}'" to {
                        exporter.export(AgentContext(agent, deployment.rulesets, allRulesets, deployment.fragments, allFragments))
                    },
                )
            }
            deployment.prompts.values.forEach { prompt ->
                add(
                    "prompt '${prompt.id}'" to {
                        exporter.export(PromptContext(prompt, deployment.rulesets, allRulesets, deployment.fragments, allFragments))
                    },
                )
            }
            deployment.skills.values.forEach { skill ->
                add(
                    "skill '${skill.id}'" to {
                        exporter.export(
                            SkillContext(
                                skill,
                                deployment.rulesets,
                                allRulesets,
                                deployment.fragments,
                                allFragments,
                                sourceDir = deployment.skillSourceDirs[skill.id],
                            ),
                        )
                    },
                )
            }
        }

        return exports.mapNotNull { (name, export) ->
            exportOrCollectFailure(manifest.id, adapter.toolType, name, export)
        }
    }

    /**
     * Builds the user deployment that is exported: every manifest of [allData] that survives the filter [manifest]
     * declares for its kind, indexed by id - the user-scope counterpart of [assembleProject].
     */
    private fun assembleUserDeployment(manifest: UserDeploymentManifest, allData: AllManifests): UserDeployment {
        val filteredSkills = filterService
            .filter(allData.skills.values, manifest.skills.filter)
            .associateBy { it.id }
        return UserDeployment(
            manifest,
            agents = filterService
                .filter(allData.agents.values, manifest.agents.filter)
                .associateBy { it.id },
            prompts = filterService
                .filter(allData.prompts.values, manifest.prompts.filter)
                .associateBy { it.id },
            rulesets = filterService
                .filter(allData.rulesets.values, manifest.rulesets.filter)
                .associateBy { it.id },
            fragments = filterService
                .filter(allData.fragments.values, manifest.fragments.filter)
                .associateBy { it.id },
            skills = filteredSkills,
            skillSourceDirs = allData.skillSourceDirs.filterKeys { it in filteredSkills },
        )
    }

    /**
     * Returns the configured adapters that deploy the manifest [manifestId], narrowed to [declaredTools] when it
     * declares any. Both kinds of deployment manifest narrow themselves the same way, under the key [toolsField] -
     * `deploy.tools` for a project, `tools` for a user deployment - which the warnings quote back to their author.
     *
     * A declared tool the run does not configure is warned about rather than failed on: the tools configured for the
     * run decide which adapters exist at all, and a manifest travels between runs that configure different sets of
     * them, so the two lists disagreeing is a difference in scope rather than a broken manifest. Narrowing to nothing
     * is warned about for the same reason it is allowed - a [subject] that exports through no tool is a deliberate
     * but silent outcome, and a silent one is worth saying out loud.
     *
     * The warning quotes the unavailable tools in the spelling a manifest writes them, not as Kotlin constants, so
     * that an author can search their own YAML for the word the engine just told them about.
     */
    private fun selectAdapters(
        manifestId: String,
        declaredTools: List<ToolType>?,
        toolsField: String,
        subject: String,
    ): List<ToolAdapter> {
        val declared = declaredTools?.toSet() ?: return tools
        val configured = tools.mapTo(mutableSetOf()) { it.toolType }
        val unavailable = declared.filterNot { it in configured }
        if (unavailable.isNotEmpty()) {
            LOG.warn("{}: {} names {}, which this run does not configure. Leaving the tool(s) out of this deploy.", manifestId, toolsField, unavailable.map { it.serialName })
        }
        val selected = tools.filter { it.toolType in declared }
        if (selected.isEmpty()) {
            LOG.warn("{}: {} selects none of the configured tools, so the {} is not exported.", manifestId, toolsField, subject)
        }
        return selected
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
            // The path is quoted because a resolved `deploy.directory` can legitimately end in `.`, which reads
            // as `..` when a sentence-ending period follows it - misleading in a warning about deletion.
            LOG.warn("{}: Replacing existing agentic files in '{}'.", project.manifest.id, destination)
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
        deploymentId: String,
        toolType: ToolType,
        manifest: String,
        export: () -> Unit,
    ): ExportFailure? = try {
        export()
        null
    } catch (ex: RulesetResolvingException) {
        LOG.error("{}: {} could not be exported for {}", deploymentId, manifest, toolType, ex)
        ExportFailure(deploymentId, toolType, manifest, ex)
    } catch (ex: FragmentResolvingException) {
        LOG.error("{}: {} could not be exported for {}", deploymentId, manifest, toolType, ex)
        ExportFailure(deploymentId, toolType, manifest, ex)
    } catch (ex: SkillFileResolvingException) {
        LOG.error("{}: {} could not be exported for {}", deploymentId, manifest, toolType, ex)
        ExportFailure(deploymentId, toolType, manifest, ex)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ToolsEngine::class.java)
    }
}

/**
 * Thrown before a single project of the run is exported, when the `deploy.directory` of at least one of them could
 * not be resolved - see [ToolsEngine.resolveDeployDirectories]. Carries every failure the run found, so that an
 * author is told about all of their broken references at once instead of one per run.
 */
class DeployDirectoryResolvingException(
    val failures: List<VariableSubstitutionException>,
) : RuntimeException(
        buildString {
            append("Cannot resolve the deploy directory of ${failures.size} project(s):")
            failures.forEach { failure -> append("\n  - ${failure.message}") }
        },
    )

/**
 * A single manifest that could not be exported, together with the resolution failure that stopped it.
 *
 * @param deploymentId the deployment the manifest was exported for - a project or a user deployment, both of which
 * export through the same isolation
 */
data class ExportFailure(
    val deploymentId: String,
    val toolType: ToolType,
    val manifest: String,
    val cause: RuntimeException,
)

/**
 * Thrown by [ToolsEngine.process] once every project has been processed, when at least one manifest failed to
 * export or a project was left unexported because its ids collide. Carries the original resolver messages so the
 * caller can report every broken reference and every collision at once.
 */
class ExportFailedException(
    val failures: List<ExportFailure>,
    val duplicates: List<DuplicateManifestId> = emptyList(),
) : RuntimeException(
        buildString {
            if (failures.isNotEmpty()) {
                // A broken manifest fails once per adapter, so counting the failures would report a single broken
                // agent as six problems with the six tools of config.yml. The count is therefore over the distinct
                // manifests, while the body still lists every adapter that could not export them.
                val brokenManifests = failures.distinctBy { it.deploymentId to it.manifest }.size
                append("Export failed for $brokenManifests manifest(s):")
                failures.forEach { failure ->
                    append("\n  - [${failure.deploymentId} | ${failure.toolType} | ${failure.manifest}] ${failure.cause.message}")
                }
            }
            if (duplicates.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append("Skipped the deployment(s) affected by ${duplicates.size} duplicate manifest id(s):")
                duplicates.forEach { duplicate -> append("\n  - ${duplicate.message}") }
            }
        },
    )
