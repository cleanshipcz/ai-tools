package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectAgents
import cz.cleanship.aitools.engine.models.ProjectFilter
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ProjectPrompts
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.services.Locations
import cz.cleanship.aitools.engine.tools.adapters.antigravity.AntigravityAdapter
import cz.cleanship.aitools.engine.tools.adapters.windsurf.WindsurfAdapter
import java.io.File

fun main() {
    ToolsEngine(
        tools = listOf(
//            WindsurfAdapter(),
            AntigravityAdapter(),
        )
    ).process(
        Locations(
            rulesets = listOf(File("01_rulesets")),
            features = listOf(),
            prompts = listOf(File("03_prompts")),
            agents = listOf(File("04_agents")),
        ),
        ProjectManifest(
            id = "test",
            description = "Test project",
            metadata = ManifestMetadata(
                version = Version("0.0.1"),
            ),
            directory = "./",
            prompts = ProjectPrompts(
                filter = ProjectFilter.ByTags(listOf("planning"))
            ),
            agents = ProjectAgents(
                filter = ProjectFilter.ByWhitelistedIds(listOf("feature-builder"))
            ),
        )
    )
}
