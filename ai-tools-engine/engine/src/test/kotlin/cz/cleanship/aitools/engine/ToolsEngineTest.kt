package cz.cleanship.aitools.engine

class ToolsEngineTest {

//    @TempDir
//    lateinit var tempDir: Path
//
//    private val engine = ToolsEngine()

//    @Test
//    fun `should process project and export tools`() {
//        // given
//        val resourcesPath = "src/test/resources"
//        val resourcesDir = File(resourcesPath)
//
//        // If src/test/resources doesn't exist (e.g. running from root), try to find it
//        val actualResourcesDir = if (resourcesDir.exists()) {
//            resourcesDir
//        } else {
//            File("engine/src/test/resources")
//        }
//
//        val locations = Locations(
//            agents = listOf(actualResourcesDir.resolve("agents")),
//            features = listOf(actualResourcesDir.resolve("features")),
//            prompts = listOf(actualResourcesDir.resolve("prompts")),
//            rulesets = listOf(actualResourcesDir.resolve("rulesets"))
//        )
//
//        val project = ProjectManifest(
//            id = "test-project",
//            description = "Test Project",
//            metadata = ManifestMetadata(version = Version.parse("1.0.0")),
//            directory = tempDir.toString()
//        )
//
//        // when
//        engine.process(locations, project)
//
//        // then
//        val windsurfDir = File(tempDir.toFile(), ".windsurf")
//        assertThat(windsurfDir).isDirectory
//
//        val rulesDir = File(windsurfDir, "rules")
//        assertThat(rulesDir).isDirectory
//        assertThat(File(rulesDir, "agent-code-reviewer.md")).exists()
//        assertThat(File(rulesDir, "prompt-git-summarize-pr.md")).exists()
//
//        val workflowsDir = File(windsurfDir, "workflows")
//        assertThat(workflowsDir).isDirectory
//        assertThat(File(workflowsDir, "feature-new-ui.md")).exists()
//    }
//
//    @Test
//    fun `should handle empty locations`() {
//        // given
//        val locations = Locations(
//            agents = emptyList(),
//            features = emptyList(),
//            prompts = emptyList(),
//            rulesets = emptyList()
//        )
//
//        val project = ProjectManifest(
//            id = "empty-project",
//            description = "Empty Project",
//            metadata = ManifestMetadata(version = Version.parse("1.0.0")),
//            directory = tempDir.toString()
//        )
//
//        // when
//        engine.process(locations, project)
//
//        // then
//        val windsurfDir = File(tempDir.toFile(), ".windsurf")
//        assertThat(windsurfDir).doesNotExist()
//    }
//
//    @Test
//    fun `should process project and export tools with filters`() {
//        // given
//        val resourcesPath = "src/test/resources"
//        val resourcesDir = File(resourcesPath)
//
//        // If src/test/resources doesn't exist (e.g. running from root), try to find it
//        val actualResourcesDir = if (resourcesDir.exists()) {
//            resourcesDir
//        } else {
//            File("engine/src/test/resources")
//        }
//
//        val locations = Locations(
//            agents = listOf(actualResourcesDir.resolve("agents")),
//            features = listOf(actualResourcesDir.resolve("features")),
//            prompts = listOf(actualResourcesDir.resolve("prompts")),
//            rulesets = listOf(actualResourcesDir.resolve("rulesets"))
//        )
//
//        val project = ProjectManifest(
//            id = "test-project",
//            description = "Test Project",
//            metadata = ManifestMetadata(version = Version.parse("1.0.0")),
//            directory = tempDir.toString(),
//            agents = ProjectAgents(filter = ProjectFilter.ByTags(listOf("review"))),
//            prompts = ProjectPrompts(filter = ProjectFilter.ByBlacklistedIds(listOf("git-summarize-pr"))),
//            features = ProjectFeatures(filter = ProjectFilter.ByWhitelistedIds(listOf("new-ui"))),
//        )
//
//        // when
//        engine.process(locations, project)
//
//        // then
//        val windsurfDir = File(tempDir.toFile(), ".windsurf")
//        assertThat(windsurfDir).isDirectory
//
//        val rulesDir = File(windsurfDir, "rules")
//        assertThat(rulesDir).isDirectory
//        assertThat(File(rulesDir, "agent-code-reviewer.md")).exists()
//        assertThat(File(rulesDir, "prompt-git-summarize-pr.md")).doesNotExist()
//
//        val workflowsDir = File(windsurfDir, "workflows")
//        assertThat(workflowsDir).isDirectory
//        assertThat(File(workflowsDir, "feature-new-ui.md")).exists()
//        assertThat(File(workflowsDir, "feature-new-ui2.md")).doesNotExist()
//    }
}
