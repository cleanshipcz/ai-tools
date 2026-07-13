package cz.cleanship.aitools.engine.services

import cz.cleanship.aitools.engine.models.InnerFeatureContext
import cz.cleanship.aitools.engine.models.Locations
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.PromptOutput
import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.models.SkillFile
import cz.cleanship.aitools.engine.models.SkillSection
import cz.cleanship.aitools.engine.models.Version
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

class LoaderServiceIntegrationTest {

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
            projects = emptyList(),
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
}
