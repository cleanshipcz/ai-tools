package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectContext
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectDocumentation
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File

class ToolFactoryTest {

    @TempDir
    lateinit var projectDir: File

    private val project = ProjectManifest(
        id = "test-project",
        description = "A project",
        metadata = ManifestMetadata(version = Version("1.0.0")),
        context = ProjectContext(documentation = ProjectDocumentation(readme = "README.md")),
        deploy = ProjectDeploy(directory = "target"),
    )

    @ParameterizedTest
    @EnumSource(ToolType::class)
    fun `should create the adapter of the requested tool`(toolType: ToolType) {
        // when
        val adapter = ToolFactory.create(toolType)

        // then
        assertThat(adapter.toolType).isEqualTo(toolType)
    }

    @ParameterizedTest
    @EnumSource(ToolType::class)
    fun `should create an adapter that writes the project when not in a dry run`(toolType: ToolType) {
        // given
        val adapter = ToolFactory.create(toolType, dryRun = false)

        // when
        adapter.export(projectDir, GlobalContext(project))

        // then
        assertThat(projectDir.walkTopDown().filter { it.isFile }.toList()).isNotEmpty()
    }

    @ParameterizedTest
    @EnumSource(ToolType::class)
    fun `should create an adapter that writes nothing in a dry run`(toolType: ToolType) {
        // given
        // - every tool has to honour the dry run, so the factory is what decides it rather than each adapter
        val adapter = ToolFactory.create(toolType, dryRun = true)

        // when
        adapter.export(projectDir, GlobalContext(project))

        // then
        assertThat(projectDir.listFiles()).isEmpty()
    }
}
