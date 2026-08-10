package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.InnerFeatureContext
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.ProjectFilter
import cz.cleanship.aitools.engine.models.PromptOutput
import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.models.SkillFile
import cz.cleanship.aitools.engine.models.SkillSection
import cz.cleanship.aitools.engine.models.ToolType
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.nio.file.Path

class LoaderServiceIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var loaderService: LoaderService

    @BeforeEach
    fun setUp() {
        loaderService = LoaderService()
    }

    @Test
    fun `should deserialize agent correctly`() {
        // given
        val file = File(javaClass.getResource("/agents/code-reviewer.yml")!!.toURI())

        // when
        val agent = loaderService.loadAgent(file)

        // then
        assertThat(agent.id).isEqualTo("code-reviewer")
        assertThat(agent.description.trim()).isEqualTo(
            """
            A senior code reviewer agent that analyzes code changes, identifies issues,
            and provides constructive, actionable feedback.
            """.trimIndent(),
        )
        assertThat(agent.rulesets).containsExactlyInAnyOrder("base")
        assertThat(agent.persona.trim()).isEqualTo(
            """
            You are a senior software engineer conducting a thorough code review.
            Your goal is to identify defects, security risks, performance issues,
            and areas for improvement while being constructive and educational.
            """.trimIndent(),
        )
        assertThat(agent.prompt.trim()).isEqualTo(
            """
            Focus on:
            - Correctness and logic errors
            - Security vulnerabilities
            - Performance bottlenecks
            - Code maintainability
            - Test coverage
            - Documentation quality
            
            Review the changes (to be provided later).

            Provide a structured review with:
            1. Critical issues (must fix)
            2. Important suggestions (should fix)
            3. Minor improvements (nice to have)
            4. Positive observations
            
            Provide specific, actionable feedback with examples where possible.
            """.trimIndent(),
        )
        assertThat(agent.constraints).containsExactlyInAnyOrder(
            "Do not approve code with security vulnerabilities.",
            "Flag missing test coverage for new functionality.",
            "Suggest specific improvements, not just criticism.",
        )
        assertThat(agent.metadata).isEqualTo(
            ManifestMetadata(
                version = Version("2.1.0"),
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = null,
                tags = setOf("review", "quality"),
            ),
        )
    }

    @Test
    fun `should deserialize prompt correctly`() {
        // given
        val file = File(javaClass.getResource("/prompts")!!.toURI()).resolve("git").resolve("summarize-pr.yml")

        // when
        val prompt = loaderService.loadPrompt(file)

        // then
        assertThat(prompt.id).isEqualTo("git-summarize-pr")
        assertThat(prompt.description).isEqualTo("Generate a concise summary of a pull request")
        assertThat(prompt.variables).containsExactlyInAnyOrder(
            PromptVariable(name = "diff", required = true, description = "The git diff to summarize"),
            PromptVariable(name = "context", required = false, description = "Additional context about the PR"),
        )
        assertThat(prompt.rules).containsExactlyInAnyOrder(
            "Be concise but comprehensive.",
            "Highlight breaking changes prominently.",
            "Group related changes together.",
            "Use bullet points for clarity.",
        )
        assertThat(prompt.rulesets).containsExactlyInAnyOrder("base")
        assertThat(prompt.content.trim()).isEqualTo(
            """
            Analyze this pull request and provide a clear summary:
            
            ```diff
            {{diff}}
            ```
            
            {{#context}}
            Context: {{context}}
            {{/context}}
            
            Provide:
            1. **Overview**: One-line summary of what this PR does
            2. **Changes**: Bullet points of key changes
            3. **Breaking Changes**: Any backward-incompatible changes (if any)
            4. **Testing**: What testing was done or is needed
            5. **Impact**: Areas of the codebase affected
            
            Format as markdown suitable for a PR description.
            """.trimIndent(),
        )
        assertThat(prompt.outputs).isEqualTo(
            PromptOutput(
                format = "markdown",
                examples = emptyList(),
            ),
        )
        assertThat(prompt.metadata).isEqualTo(
            ManifestMetadata(
                version = Version("1.0.0"),
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = null,
                tags = setOf("docs", "pr", "git"),
            ),
        )
    }

    @Test
    fun `should deserialize ruleset correctly`() {
        // given
        val file = File(javaClass.getResource("/rulesets/base.yml")!!.toURI())

        // when
        val ruleset = loaderService.loadRuleset(file)

        // then
        assertThat(ruleset.id).isEqualTo("base")
        assertThat(ruleset.description).isEqualTo("Base rules applicable to all agents and prompts")
        assertThat(ruleset.rules).containsExactlyInAnyOrder(
            "Be precise and accurate in your responses.",
            "Follow the user's requirements carefully and to the letter.",
            "If you are unsure, ask for clarification instead of guessing.",
            "Break complex tasks into smaller, manageable steps.",
            "Verify your work before presenting it.",
        )
        assertThat(ruleset.metadata).isEqualTo(
            ManifestMetadata(
                version = Version("1.1.0"),
                author = "AI Tools Team",
                created = "2025-01-01",
                updated = "2025-11-16",
                tags = setOf("foundation", "general"),
            ),
        )
    }

    @Test
    fun `should deserialize sections-based skill correctly`() {
        // given
        val file = File(javaClass.getResource("/skills/run-detekt.yml")!!.toURI())

        // when
        val skill = loaderService.loadSkill(file)

        // then
        assertThat(skill.id).isEqualTo("run-detekt")
        assertThat(skill.description).isEqualTo("Run Detekt static analysis for Kotlin. Use when user asks to run detekt.")
        assertThat(skill.sections).hasSize(1)
        assertThat(skill.sections[0]).isInstanceOf(SkillSection.TextSection::class.java)
        val textSection = skill.sections[0] as SkillSection.TextSection
        assertThat(textSection.text.trim()).contains("Run `./gradlew detekt`")
        assertThat(skill.metadata).isEqualTo(
            ManifestMetadata(
                version = Version("1.0.0"),
                author = "AI Tools Team",
                created = "2025-01-01",
                tags = setOf("detekt", "kotlin", "lint", "static-analysis"),
            ),
        )
    }

    @Test
    fun `should deserialize skill with text sections correctly`() {
        // given
        val file = File(javaClass.getResource("/skills/search-repo.yml")!!.toURI())

        // when
        val skill = loaderService.loadSkill(file)

        // then
        assertThat(skill.id).isEqualTo("search-repo")
        assertThat(skill.description).isEqualTo("Search repository for code patterns or text. Use when user asks to search the codebase.")
        assertThat(skill.sections).hasSize(1)
        assertThat(skill.sections[0]).isInstanceOf(SkillSection.TextSection::class.java)
        assertThat(skill.metadata).isEqualTo(
            ManifestMetadata(
                version = Version("1.0.0"),
                author = "AI Tools Team",
                created = "2025-01-01",
                tags = setOf("search", "filesystem"),
            ),
        )
    }

    @Test
    fun `should deserialize fragment correctly`() {
        // given
        val file = File(javaClass.getResource("/fragments/confluence-guide.yml")!!.toURI())

        // when
        val fragment = loaderService.loadFragment(file)

        // then
        assertThat(fragment.id).isEqualTo("confluence-guide")
        assertThat(fragment.description).isEqualTo("Guide for formatting Confluence pages")
        assertThat(fragment.content.trim()).isEqualTo(
            """
            Use headings (h1-h6) to structure content hierarchically.
            Use tables for structured data comparison.
            Use code blocks with language hints for code snippets.
            Use info/warning/note panels for callouts.
            """.trimIndent(),
        )
        assertThat(fragment.metadata).isEqualTo(
            ManifestMetadata(
                version = Version("1.0.0"),
                author = "AI Tools Team",
                created = "2026-04-03",
                updated = null,
                tags = setOf("confluence", "formatting"),
            ),
        )
    }

    @Test
    fun `should deserialize directory-based skill with files`() {
        // given
        val file = File(javaClass.getResource("/skills/dir-skill/skill.yml")!!.toURI())

        // when
        val skill = loaderService.loadSkill(file)

        // then
        assertThat(skill.id).isEqualTo("dir-skill")
        assertThat(skill.description).isEqualTo("A directory-based skill for testing. Use when user asks for dir skill.")
        assertThat(skill.sections).hasSize(1)
        assertThat(skill.sections[0]).isInstanceOf(SkillSection.TextSection::class.java)
        assertThat(skill.files).hasSize(2)
        // Short form: source == target
        assertThat(skill.files[0]).isEqualTo(SkillFile(source = "templates/example.txt", target = "templates/example.txt"))
        // Long form: explicit source and target
        assertThat(skill.files[1]).isEqualTo(
            SkillFile(source = "/absolute/path/to/shared.txt", target = "references/shared.txt"),
        )
    }

    @Test
    fun `should load directory-based skills via loadAll`() {
        // given
        val skillsDir = File(javaClass.getResource("/skills")!!.toURI())
        val locations = Locations(
            agents = emptyList(),
            prompts = emptyList(),
            rulesets = emptyList(),
            fragments = emptyList(),
            skills = listOf(skillsDir),
            deployments = emptyList(),
        )

        // when
        val allManifests = loaderService.loadAll(locations)

        // then
        // Should load both standalone .yml skills and directory-based skills
        assertThat(allManifests.skills).containsKey("run-detekt")
        assertThat(allManifests.skills).containsKey("search-repo")
        assertThat(allManifests.skills).containsKey("dir-skill")
        // Directory-based skill should have a source dir entry
        assertThat(allManifests.skillSourceDirs).containsKey("dir-skill")
        assertThat(allManifests.skillSourceDirs["dir-skill"]!!.name).isEqualTo("dir-skill")
        // Standalone skills should NOT have source dir entries
        assertThat(allManifests.skillSourceDirs).doesNotContainKey("run-detekt")
    }

    @Test
    fun `should deserialize feature correctly`() {
        // given
        val file = File(javaClass.getResource("/features/new-ui.yml")!!.toURI())

        // when
        val feature = loaderService.loadFeature(file)

        // then
        assertThat(feature.id).isEqualTo("new-ui")
        assertThat(feature.description).isEqualTo("Create a new UI for the application")
        assertThat(feature.context).isEqualTo(
            InnerFeatureContext(
                overview = "The goal is to modernize the user interface using React and Tailwind CSS.\n",
                architecture = "Standard React architecture with functional components and hooks.\n",
                dependencies = listOf("react", "tailwindcss"),
                files = listOf("src/App.tsx", "src/components/Dashboard.tsx"),
            ),
        )
        assertThat(feature.prompt.trim()).isEqualTo("Follow the design guidelines in the UI kit.")
        assertThat(feature.acceptanceCriteria).containsExactlyInAnyOrder(
            "Responsive design (mobile, tablet, desktop)",
            "Dark mode support",
            "Accessibility (WCAG 2.1 AA)",
        )
        assertThat(feature.constraints).containsExactlyInAnyOrder(
            "Use functional components",
            "No class components",
        )
        assertThat(feature.metadata).isEqualTo(
            ManifestMetadata(
                version = Version("1.0.0"),
                author = "AI Tools Team",
                created = "2025-01-01",
                tags = setOf("ui", "react"),
            ),
        )
    }

    @Nested
    inner class MalformedVersions {

        @ParameterizedTest
        @CsvSource("1.0", "v1.0.0", "1.0.0.4", "latest")
        fun `should fail naming the file when a manifest declares a malformed version`(version: String) {
            // given
            val file = writeRuleset("broken.yml", version)

            // when
            val error = runCatching { loaderService.loadRuleset(file) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ManifestLoadingException::class.java)
                .hasMessageContaining(file.absolutePath)
                .hasMessageContaining(version)
                .hasMessageContaining("MAJOR.MINOR.PATCH")
        }

        @Test
        fun `should fail naming the file when loading every manifest reaches a malformed version`() {
            // given
            // - loadAll is what the CLI actually calls, and it must name the file just as the single loader does
            val file = writeRuleset("broken.yml", "1.0")

            // when
            val error = runCatching { loaderService.loadAll(rulesetLocations()) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ManifestLoadingException::class.java)
                .hasMessageContaining(file.absolutePath)
        }

        @Test
        fun `should load the manifest when the version is well-formed`() {
            // given
            val file = writeRuleset("valid.yml", "1.2.3-alpha")

            // when
            val ruleset = loaderService.loadRuleset(file)

            // then
            assertThat(ruleset.metadata.version).isEqualTo(Version(1, 2, 3, "alpha"))
        }

        private fun writeRuleset(fileName: String, version: String): File {
            val directory = tempDir.resolve("malformed-versions").toFile()
            directory.mkdirs()
            val file = directory.resolve(fileName)
            file.writeText(
                "id: broken\ndescription: A ruleset\nrules:\n  - A rule.\nmetadata:\n  version: $version\n",
            )
            return file
        }

        private fun rulesetLocations() = Locations(
            agents = emptyList(),
            deployments = emptyList(),
            prompts = emptyList(),
            rulesets = listOf(tempDir.resolve("malformed-versions").toFile()),
            fragments = emptyList(),
            skills = emptyList(),
        )
    }

    @Nested
    inner class DuplicateManifestIds {

        @ParameterizedTest
        @CsvSource("agents", "prompts", "rulesets", "fragments")
        fun `should fail naming the id and both files when two manifests share an id`(kind: String) {
            // given
            // - two manifest files in the same location declaring the very same id
            val directory = tempDir.resolve(kind).toFile()
            val firstFile = writeManifest(directory, "first.yml", kind, "duplicated-id")
            val secondFile = writeManifest(directory, "second.yml", kind, "duplicated-id")

            // when
            val error = runCatching { loaderService.loadAll(locationsFor(kind, directory)) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(DuplicateManifestIdException::class.java)
                .hasMessageContaining("duplicated-id")
                .hasMessageContaining(firstFile.absolutePath)
                .hasMessageContaining(secondFile.absolutePath)
        }

        @Test
        fun `should fail naming the id and both files when two skills share an id`() {
            // given
            val directory = tempDir.resolve("skills").toFile()
            val firstFile = writeSkill(directory, "first.yml", "duplicated-skill")
            val secondFile = writeSkill(directory, "second.yml", "duplicated-skill")

            // when
            val error = runCatching { loaderService.loadAll(locationsFor("skills", directory)) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(DuplicateManifestIdException::class.java)
                .hasMessageContaining("duplicated-skill")
                .hasMessageContaining(firstFile.absolutePath)
                .hasMessageContaining(secondFile.absolutePath)
        }

        @Test
        fun `should name every file when three manifests share an id`() {
            // given
            // - the third file must not quietly take the place of the pair that was dropped
            val directory = tempDir.resolve("rulesets").toFile()
            val firstFile = writeManifest(directory, "first.yml", "rulesets", "duplicated-id")
            val secondFile = writeManifest(directory, "second.yml", "rulesets", "duplicated-id")
            val thirdFile = writeManifest(directory, "third.yml", "rulesets", "duplicated-id")

            // when
            val error = runCatching { loaderService.loadAll(locationsFor("rulesets", directory)) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(DuplicateManifestIdException::class.java)
                .hasMessageContaining(firstFile.absolutePath)
                .hasMessageContaining(secondFile.absolutePath)
                .hasMessageContaining(thirdFile.absolutePath)
        }

        @Test
        fun `should report every duplicate of a single run together`() {
            // given
            // - two shared kinds collide and a project collides too, so one run has to name all three
            val rulesetsDirectory = tempDir.resolve("rulesets").toFile()
            writeManifest(rulesetsDirectory, "first.yml", "rulesets", "duplicated-ruleset")
            writeManifest(rulesetsDirectory, "second.yml", "rulesets", "duplicated-ruleset")
            val fragmentsDirectory = tempDir.resolve("fragments").toFile()
            writeManifest(fragmentsDirectory, "first.yml", "fragments", "duplicated-fragment")
            writeManifest(fragmentsDirectory, "second.yml", "fragments", "duplicated-fragment")
            val projectsDirectory = tempDir.resolve("projects").toFile()
            writeProject(projectsDirectory.resolve("alpha"), "duplicated-project")
            writeProject(projectsDirectory.resolve("beta"), "duplicated-project")
            val locations = Locations(
                agents = emptyList(),
                deployments = listOf(projectsDirectory),
                prompts = emptyList(),
                rulesets = listOf(rulesetsDirectory),
                fragments = listOf(fragmentsDirectory),
                skills = emptyList(),
            )

            // when
            val error = runCatching { loaderService.loadAll(locations) }.exceptionOrNull()

            // then
            assertThat(error).isInstanceOf(DuplicateManifestIdException::class.java)
            assertThat((error as DuplicateManifestIdException).duplicates.map { it.id })
                .containsExactlyInAnyOrder("duplicated-ruleset", "duplicated-fragment", "duplicated-project")
        }

        @Test
        fun `should drop both projects and keep the others when two projects share an id`() {
            // given
            // - projects live in their own directory, each holding a project.yml
            val directory = tempDir.resolve("projects").toFile()
            val alphaFile = writeProject(directory.resolve("alpha"), "duplicated-project")
            val betaFile = writeProject(directory.resolve("beta"), "duplicated-project")
            writeProject(directory.resolve("gamma"), "healthy-project")

            // when
            val allManifests = loaderService.loadAll(locationsFor("deployments", directory))

            // then
            // - neither colliding project can be exported, but the project that shares nothing with them still is
            assertThat(allManifests.projects).containsOnlyKeys("healthy-project")
            val duplicate = allManifests.duplicates.single()
            assertThat(duplicate.id).isEqualTo("duplicated-project")
            // - which of the two files is found first is up to the filesystem; both of them have to be named
            assertThat(listOf(duplicate.firstFile, duplicate.secondFile)).containsExactlyInAnyOrder(alphaFile, betaFile)
            assertThat(duplicate.message)
                .contains("duplicated-project")
                .contains(alphaFile.absolutePath)
                .contains(betaFile.absolutePath)
        }

        @Test
        fun `should drop the project and keep the others when two of its features share an id`() {
            // given
            // - a project with no feature filter deploys every one of its features, so an ambiguous feature id
            //   makes that whole project unexportable while its neighbours stay untouched
            val directory = tempDir.resolve("projects").toFile()
            writeProject(directory.resolve("alpha"), "alpha-project")
            val firstFile = writeFeature(directory.resolve("alpha"), "first.yml", "duplicated-feature")
            val secondFile = writeFeature(directory.resolve("alpha"), "second.yml", "duplicated-feature")
            writeProject(directory.resolve("beta"), "beta-project")

            // when
            val allManifests = loaderService.loadAll(locationsFor("deployments", directory))

            // then
            assertThat(allManifests.projects).containsOnlyKeys("beta-project")
            val duplicate = allManifests.duplicates.single()
            assertThat(duplicate.id).isEqualTo("duplicated-feature")
            assertThat(duplicate.message)
                .contains(firstFile.absolutePath)
                .contains(secondFile.absolutePath)
        }

        @Test
        fun `should report no duplicate when every project has its own ids`() {
            // given
            val directory = tempDir.resolve("projects").toFile()
            writeProject(directory.resolve("alpha"), "alpha-project")
            writeFeature(directory.resolve("alpha"), "shared.yml", "shared-feature")
            writeProject(directory.resolve("beta"), "beta-project")
            // - the very same feature id in another project is scoped to that project, not a collision
            writeFeature(directory.resolve("beta"), "shared.yml", "shared-feature")

            // when
            val allManifests = loaderService.loadAll(locationsFor("deployments", directory))

            // then
            assertThat(allManifests.projects).containsOnlyKeys("alpha-project", "beta-project")
            assertThat(allManifests.duplicates).isEmpty()
        }

        @Test
        fun `should load both manifests when ids are unique`() {
            // given
            val directory = tempDir.resolve("rulesets").toFile()
            writeManifest(directory, "first.yml", "rulesets", "first-ruleset")
            writeManifest(directory, "second.yml", "rulesets", "second-ruleset")

            // when
            val allManifests = loaderService.loadAll(locationsFor("rulesets", directory))

            // then
            assertThat(allManifests.rulesets).containsOnlyKeys("first-ruleset", "second-ruleset")
        }

        @Test
        fun `should load manifest once when the same directory is listed twice`() {
            // given
            // - overlapping locations make findYamlFiles yield the very same file more than once,
            //   which must not be mistaken for two manifests sharing an id
            val directory = tempDir.resolve("rulesets").toFile()
            writeManifest(directory, "only.yml", "rulesets", "only-ruleset")
            val locations = locationsFor("rulesets", directory).copy(rulesets = listOf(directory, directory))

            // when
            val allManifests = loaderService.loadAll(locations)

            // then
            assertThat(allManifests.rulesets).containsOnlyKeys("only-ruleset")
        }

        private fun writeManifest(directory: File, fileName: String, kind: String, id: String): File {
            val body = when (kind) {
                "agents" -> "persona: A persona\nprompt: A prompt\n"
                "prompts" -> "content: Some content\n"
                "rulesets" -> "rules:\n  - A rule.\n"
                "fragments" -> "content: Some content\n"
                else -> error("Unsupported manifest kind: $kind")
            }
            return writeYaml(directory, fileName, "id: $id\ndescription: A manifest\n$body")
        }

        private fun writeSkill(directory: File, fileName: String, id: String): File =
            writeYaml(directory, fileName, "id: $id\ndescription: A skill\nsections:\n  - text: Some text\n")

        private fun writeFeature(projectDirectory: File, fileName: String, id: String): File = writeYaml(
            projectDirectory.resolve("features"),
            fileName,
            "id: $id\ndescription: A feature\nprompt: A feature prompt\n",
        )

        private fun writeProject(directory: File, id: String): File = writeYaml(
            directory,
            "project.yml",
            """
            id: $id
            description: A project
            context:
              documentation:
                readme: README.md
            deploy:
              directory: "${directory.absolutePath}"

            """.trimIndent(),
        )

        private fun writeYaml(directory: File, fileName: String, content: String): File {
            directory.mkdirs()
            val file = directory.resolve(fileName)
            file.writeText(content + "metadata:\n  version: 1.0.0\n")
            return file
        }

        private fun locationsFor(kind: String, directory: File): Locations {
            val directories = listOf(directory)
            return Locations(
                agents = directories.takeIf { kind == "agents" } ?: emptyList(),
                deployments = directories.takeIf { kind == "deployments" } ?: emptyList(),
                prompts = directories.takeIf { kind == "prompts" } ?: emptyList(),
                rulesets = directories.takeIf { kind == "rulesets" } ?: emptyList(),
                fragments = directories.takeIf { kind == "fragments" } ?: emptyList(),
                skills = directories.takeIf { kind == "skills" } ?: emptyList(),
            )
        }
    }

    @Nested
    inner class UserDeployments {

        @Test
        fun `should load a user deployment from a user yml beside the projects`() {
            // given
            // - both kinds live under the same configured location, the filename deciding which schema is read
            val directory = tempDir.resolve("deployments").toFile()
            writeProject(directory.resolve("ai-tools"), "ai-tools")
            writeUserDeployment(directory.resolve("globals"), "globals")

            // when
            val allManifests = loaderService.loadAll(deploymentLocations(directory))

            // then
            assertThat(allManifests.userDeployments).containsOnlyKeys("globals")
            assertThat(allManifests.projects).containsOnlyKeys("ai-tools")
            assertThat(allManifests.userDeployments.getValue("globals").description).isEqualTo("A user deployment")
        }

        @Test
        fun `should load the user deployments of every configured location`() {
            // given
            // - a machine-private location beside the one the repository ships, as config.local.yml adds
            val sharedDirectory = tempDir.resolve("deployments").toFile()
            val privateDirectory = tempDir.resolve("private-deployments").toFile()
            writeUserDeployment(sharedDirectory.resolve("globals"), "globals")
            writeUserDeployment(privateDirectory.resolve("machine"), "machine")

            // when
            val allManifests = loaderService.loadAll(
                deploymentLocations(sharedDirectory).copy(deployments = listOf(sharedDirectory, privateDirectory)),
            )

            // then
            assertThat(allManifests.userDeployments).containsOnlyKeys("globals", "machine")
        }

        @Test
        fun `should read the filters of a user deployment`() {
            // given
            val directory = tempDir.resolve("deployments").toFile()
            writeYaml(
                directory.resolve("globals"),
                "user.yml",
                """
                id: globals
                description: A user deployment
                tools:
                  - claude
                rulesets:
                  filter:
                    - type: tags
                      tags:
                        - global

                """.trimIndent(),
            )

            // when
            val allManifests = loaderService.loadAll(deploymentLocations(directory))

            // then
            val deployment = allManifests.userDeployments.getValue("globals")
            assertThat(deployment.tools).containsExactly(ToolType.CLAUDE)
            assertThat(deployment.rulesets.filter).containsExactly(ProjectFilter.ByTags(listOf("global")))
        }

        @Test
        fun `should fail naming the file when a user deployment declares an unknown field`() {
            // given
            // - the same strict decoding every other kind is loaded with, so a typo is reported instead of ignored
            val directory = tempDir.resolve("deployments").toFile()
            val file = writeYaml(
                directory.resolve("globals"),
                "user.yml",
                "id: globals\ndescription: A user deployment\ndirectory: /home/user\n",
            )

            // when
            val error = runCatching { loaderService.loadAll(deploymentLocations(directory)) }.exceptionOrNull()

            // then
            // - the message names the file and the failure behind it names the key, exactly as for every other kind
            assertThat(error)
                .hasMessageContaining(file.absolutePath)
                .hasStackTraceContaining("Unknown property 'directory'")
        }

        @Test
        fun `should fail naming the file when a user deployment declares a malformed version`() {
            // given
            val directory = tempDir.resolve("deployments").toFile()
            val file = directory.resolve("globals").also { it.mkdirs() }.resolve("user.yml")
            file.writeText("id: globals\ndescription: A user deployment\nmetadata:\n  version: 1.0\n")

            // when
            val error = runCatching { loaderService.loadAll(deploymentLocations(directory)) }.exceptionOrNull()

            // then
            assertThat(error)
                .isInstanceOf(ManifestLoadingException::class.java)
                .hasMessageContaining(file.absolutePath)
        }

        @Test
        fun `should drop both user deployments and keep the others when two of them share an id`() {
            // given
            val directory = tempDir.resolve("deployments").toFile()
            val firstFile = writeUserDeployment(directory.resolve("alpha"), "duplicated-deployment")
            val secondFile = writeUserDeployment(directory.resolve("beta"), "duplicated-deployment")
            writeUserDeployment(directory.resolve("gamma"), "healthy-deployment")

            // when
            val allManifests = loaderService.loadAll(deploymentLocations(directory))

            // then
            assertThat(allManifests.userDeployments).containsOnlyKeys("healthy-deployment")
            val duplicate = allManifests.duplicates.single()
            assertThat(duplicate.id).isEqualTo("duplicated-deployment")
            assertThat(listOf(duplicate.firstFile, duplicate.secondFile)).containsExactlyInAnyOrder(firstFile, secondFile)
        }

        @Test
        fun `should load both when a user deployment and a project share an id`() {
            // given
            // - the two kinds are indexed apart, so the same name may describe a project and a user deployment
            val directory = tempDir.resolve("deployments").toFile()
            writeProject(directory.resolve("ai-tools"), "shared-id")
            writeUserDeployment(directory.resolve("globals"), "shared-id")

            // when
            val allManifests = loaderService.loadAll(deploymentLocations(directory))

            // then
            assertThat(allManifests.projects).containsOnlyKeys("shared-id")
            assertThat(allManifests.userDeployments).containsOnlyKeys("shared-id")
            assertThat(allManifests.duplicates).isEmpty()
        }

        @Test
        fun `should load a user deployment once when the same directory is configured twice`() {
            // given
            val directory = tempDir.resolve("deployments").toFile()
            writeUserDeployment(directory.resolve("globals"), "globals")

            // when
            val allManifests = loaderService.loadAll(
                deploymentLocations(directory).copy(deployments = listOf(directory, directory)),
            )

            // then
            assertThat(allManifests.userDeployments).containsOnlyKeys("globals")
            assertThat(allManifests.duplicates).isEmpty()
        }

        private fun writeUserDeployment(directory: File, id: String): File =
            writeYaml(directory, "user.yml", "id: $id\ndescription: A user deployment\n")

        private fun writeProject(directory: File, id: String): File = writeYaml(
            directory,
            "project.yml",
            """
            id: $id
            description: A project
            context:
              documentation:
                readme: README.md
            deploy:
              directory: "${directory.absolutePath}"

            """.trimIndent(),
        )

        private fun writeYaml(directory: File, fileName: String, content: String): File {
            directory.mkdirs()
            val file = directory.resolve(fileName)
            file.writeText(content + "metadata:\n  version: 1.0.0\n")
            return file
        }

        private fun deploymentLocations(directory: File) = Locations(
            agents = emptyList(),
            deployments = listOf(directory),
            prompts = emptyList(),
            rulesets = emptyList(),
            fragments = emptyList(),
            skills = emptyList(),
        )
    }
}
