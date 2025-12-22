package cz.cleanship.aitools.engine

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.services.Locations
import java.io.File

fun main() {
    ToolsEngine().process(
        Locations(
            rulepacks = listOf(File("01_rulepacks")),
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
            directory = File("build"),
        )
    )
}
