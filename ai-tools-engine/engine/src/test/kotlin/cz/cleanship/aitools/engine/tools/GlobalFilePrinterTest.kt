package cz.cleanship.aitools.engine.tools

import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectContext
import cz.cleanship.aitools.engine.models.ProjectDeploy
import cz.cleanship.aitools.engine.models.ProjectDocumentation
import cz.cleanship.aitools.engine.models.ProjectDocumentationItem
import cz.cleanship.aitools.engine.models.ProjectManifest
import cz.cleanship.aitools.engine.models.Version
import cz.cleanship.aitools.engine.utils.getExpectedOutput
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GlobalFilePrinterTest {

    private val printer = GlobalFilePrinter()

    @Test
    fun `should list the readme alone when the project documents nothing else`() {
        // given
        val project = project(ProjectDocumentation(readme = "README.md"))

        // when
        val content = getExpectedOutput(printer, GlobalContext(project))

        // then
        assertThat(content).isEqualTo(
            """
            # test-project

            A project

            ## Documentation files

            - README.md

            """.trimIndent() + "\n",
        )
    }

    @Test
    fun `should nest the files of every topic group under its name in declaration order`() {
        // given
        // - a map keeps its insertion order, which is the order the manifest author wrote the groups in
        val project = project(
            ProjectDocumentation(
                perTopic = linkedMapOf(
                    "main_docs" to linkedMapOf(
                        "prompts" to "03_prompts/README.md",
                        "skills" to "04_skills/README.md",
                    ),
                    "development" to linkedMapOf(
                        "style_guide" to "90_docs/STYLE_GUIDE.md",
                    ),
                ),
            ),
        )

        // when
        val content = getExpectedOutput(printer, GlobalContext(project))

        // then
        assertThat(content).isEqualTo(
            """
            # test-project

            A project

            ## Documentation files

            - main_docs:
              - prompts: 03_prompts/README.md
              - skills: 04_skills/README.md
            - development:
              - style_guide: 90_docs/STYLE_GUIDE.md

            """.trimIndent() + "\n",
        )
    }

    @Test
    fun `should list the readme first, then the topic groups, then the additional files`() {
        // given
        val project = project(
            ProjectDocumentation(
                readme = "README.md",
                perTopic = linkedMapOf(
                    "main_docs" to linkedMapOf(
                        "prompts" to "03_prompts/README.md",
                        "skills" to "04_skills/README.md",
                    ),
                    "development" to linkedMapOf(
                        "style_guide" to "90_docs/STYLE_GUIDE.md",
                    ),
                ),
                additional = listOf(ProjectDocumentationItem(path = "docs/architecture.md", description = "System design")),
            ),
        )

        // when
        val content = getExpectedOutput(printer, GlobalContext(project))

        // then
        assertThat(content).isEqualTo(
            """
            # test-project

            A project

            ## Documentation files

            - README.md
            - main_docs:
              - prompts: 03_prompts/README.md
              - skills: 04_skills/README.md
            - development:
              - style_guide: 90_docs/STYLE_GUIDE.md
            - docs/architecture.md - (System design)

            """.trimIndent() + "\n",
        )
    }

    @Test
    fun `should omit the documentation section when the project declares no documentation`() {
        // given
        val project = project(ProjectDocumentation())

        // when
        val content = getExpectedOutput(printer, GlobalContext(project))

        // then
        assertThat(content).isEqualTo(
            """
            # test-project

            A project

            """.trimIndent() + "\n",
        )
    }

    private fun project(documentation: ProjectDocumentation) = ProjectManifest(
        id = "test-project",
        description = "A project",
        metadata = ManifestMetadata(version = Version("1.0.0")),
        context = ProjectContext(documentation = documentation),
        deploy = ProjectDeploy(directory = "target"),
    )
}
