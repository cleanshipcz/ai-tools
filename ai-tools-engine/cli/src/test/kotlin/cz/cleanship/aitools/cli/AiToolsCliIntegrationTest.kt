package cz.cleanship.aitools.cli

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.parse
import cz.cleanship.aitools.engine.ExportFailedException
import cz.cleanship.aitools.engine.ExportFailure
import cz.cleanship.aitools.engine.models.DuplicateManifestId
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.services.DuplicateManifestIdException
import cz.cleanship.aitools.engine.services.ManifestLoadingException
import cz.cleanship.aitools.engine.tools.RulesetResolvingException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AiToolsCliIntegrationTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should run tools application when config exists`() {
        // given
        File(tempDir, "config.yml").writeText("{}")
        val cli = AiToolsCli()

        // when
        val error = runCatching {
            cli.main(arrayOf("--working-dir", tempDir.absolutePath))
        }.exceptionOrNull()

        // then
        assertThat(error).isNull()
    }

    @Test
    fun `should fail with the resolver message when an export fails`() {
        // given
        // - a CliktError makes the command report on stderr and exit with a non-zero status code
        val failure = ExportFailure(
            projectId = "test-project",
            toolType = ToolType.CLAUDE,
            manifest = "agent 'broken-agent'",
            cause = RulesetResolvingException(
                pattern = "missing-ruleset",
                requestedBy = "agent 'broken-agent'",
                availableIds = listOf("base"),
            ),
        )
        val cli = AiToolsCli(runner = { throw ExportFailedException(listOf(failure)) })

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("No rulesets match pattern 'missing-ruleset'")
            .hasMessageContaining("agent 'broken-agent'")
        assertThat((error as CliktError).statusCode).isNotZero()
    }

    @Test
    fun `should fail with the file path when a manifest declares a malformed version`() {
        // given
        val cli = AiToolsCli(
            runner = {
                throw ManifestLoadingException(
                    file = File("/manifests/broken.yml"),
                    cause = IllegalArgumentException("Invalid version format: 1.0"),
                )
            },
        )

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("broken.yml")
            .hasMessageContaining("Invalid version format: 1.0")
        assertThat((error as CliktError).statusCode).isNotZero()
    }

    @Test
    fun `should fail with both file paths when a project was skipped for a duplicate id`() {
        // given
        // - the other projects exported fine, so only the duplicate keeps the run from succeeding
        val firstFile = File("/projects/alpha/project.yml")
        val secondFile = File("/projects/beta/project.yml")
        val cli = AiToolsCli(
            runner = {
                throw ExportFailedException(
                    failures = emptyList(),
                    duplicates = listOf(
                        DuplicateManifestId(id = "duplicated-id", firstFile = firstFile, secondFile = secondFile),
                    ),
                )
            },
        )

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("duplicated-id")
            .hasMessageContaining(firstFile.absolutePath)
            .hasMessageContaining(secondFile.absolutePath)
        assertThat((error as CliktError).statusCode).isNotZero()
    }

    @Test
    fun `should fail with both file paths when a duplicate manifest id is loaded`() {
        // given
        val cli = AiToolsCli(
            runner = {
                throw DuplicateManifestIdException(
                    listOf(
                        DuplicateManifestId(
                            id = "duplicated-id",
                            firstFile = File("/manifests/first.yml"),
                            secondFile = File("/manifests/second.yml"),
                        ),
                    ),
                )
            },
        )

        // when
        val error = runCatching { cli.parse(arrayOf("--working-dir", tempDir.absolutePath)) }.exceptionOrNull()

        // then
        assertThat(error)
            .isInstanceOf(CliktError::class.java)
            .hasMessageContaining("duplicated-id")
            .hasMessageContaining("first.yml")
            .hasMessageContaining("second.yml")
        assertThat((error as CliktError).statusCode).isNotZero()
    }
}
