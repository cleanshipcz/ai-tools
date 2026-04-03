package cz.cleanship.aitools.engine.data

import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.FeatureManifest
import cz.cleanship.aitools.engine.models.FragmentManifest
import cz.cleanship.aitools.engine.models.InnerFeatureContext
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.PromptOutput
import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.models.RulesetManifest
import cz.cleanship.aitools.engine.models.SkillCommand
import cz.cleanship.aitools.engine.models.SkillInput
import cz.cleanship.aitools.engine.models.SkillManifest
import cz.cleanship.aitools.engine.models.SkillOutputFile
import cz.cleanship.aitools.engine.models.SkillOutputs
import cz.cleanship.aitools.engine.models.Version

val ruleset = RulesetManifest(
    id = "test-ruleset",
    description =
        """
        Multiline
        description
        """.trimIndent(),
    rules = listOf(
        "Rule number one.",
        "Rule number two.",
        "Rule number three.",
    ),
    metadata = ManifestMetadata(
        version = Version("1.2.3"),
    ),
)

val expectedRuleset =
    """
    # test-ruleset
    
    Multiline
    description
    
    ## Rules
    
    - Rule number one.
    - Rule number two.
    - Rule number three.

    """.trimIndent()

val rulesets = mapOf(
    ruleset.id to ruleset,
)

val agent = AgentManifest(
    id = "test-agent",
    description =
        """
        Multiline
        description
        """.trimIndent(),
    rulesets = listOf("test-ruleset"),
    rules = listOf("rule1", "rule2"),
    persona =
        """
        Multiline
        persona
        """.trimIndent(),
    prompt =
        """
        Multiline
        system prompt
        """.trimIndent(),
    constraints = listOf("Constraint one.", "Constraint two."),
    metadata = ManifestMetadata(
        version = Version("1.2.3"),
        author = "Test Author",
        created = "2025-01-01",
    ),
)

val expectedAgent =
    """
    # test-agent

    Multiline
    description
    
    ## Persona
    
    Multiline
    persona
    
    ## Rules
    
    - Rule number one.
    - Rule number two.
    - Rule number three.
    - rule1
    - rule2
    
    ## Prompt
    
    Multiline
    system prompt
    
    ## Constraints
    
    - Constraint one.
    - Constraint two.
    
    """.trimIndent()

val prompt = PromptManifest(
    id = "id#1",
    description =
        """
        Multiline
        description#1
        """.trimIndent(),
    variables = listOf(
        PromptVariable(
            name = "var1",
            required = true,
            description =
                """
                Multiline
                description#2
                """.trimIndent(),
        ),
        PromptVariable(
            name = "var2",
            required = false,
            description =
                """
                Multiline
                description#3
                """.trimIndent(),
        ),
    ),
    rulesets = listOf("test-ruleset"),
    rules = listOf("rule1", "rule2"),
    content =
        """
        Multiline
        content#3
        """.trimIndent(),
    outputs = PromptOutput(format = "format1", examples = listOf("example1", "example2")),
    metadata = ManifestMetadata(
        version = Version("1.2.3"),
        author = "Test Author",
        created = "2025-01-01",
    ),
)

val expectedPrompt =
    """
    # id#1
    
    Multiline
    description#1
    
    ## Variables
    
    - `{{var1}}` (required): Multiline
      description#2
    - `{{var2}}`: Multiline
      description#3
    
    ## Rules
    
    - Rule number one.
    - Rule number two.
    - Rule number three.
    - rule1
    - rule2
    
    ## Prompt
    
    Multiline
    content#3
    
    """.trimIndent()

val feature = FeatureManifest(
    id = "id#1",
    description =
        """
        Multiline
        description
        """.trimIndent(),
    context = InnerFeatureContext(
        overview =
            """
            Multiline
            overview
            """.trimIndent(),
        architecture =
            """
            Multiline
            architecture
            """.trimIndent(),
        dependencies = listOf(
            "Dependency#1",
            "Dependency#2",
        ),
        files = listOf(
            "File#1",
            "File#2",
        ),
    ),
    prompt =
        """
        Multiline
        prompt
        """.trimIndent(),
    acceptanceCriteria = listOf(
        "Acceptance criteria#1",
        "Acceptance criteria#2",
    ),
    constraints = listOf(
        "Constraint#1",
        "Constraint#2",
    ),
    metadata = ManifestMetadata(
        version = Version("1.2.3"),
        author = "Test Author",
        created = "2025-01-01",
    ),
)

val expectedFeature =
    """
    # id#1
    
    Multiline
    description
    
    ## Context
    
    ### Overview
    
    Multiline
    overview
    
    ### Architecture
    
    Multiline
    architecture
    
    ### Dependencies
    
    - Dependency#1
    - Dependency#2
    
    ### Files
    
    - File#1
    - File#2
    
    ## Prompt
    
    Multiline
    prompt
    
    ## Acceptance Criteria
    
    - Acceptance criteria#1
    - Acceptance criteria#2
    
    ## Constraints

    - Constraint#1
    - Constraint#2

    """.trimIndent()

val commandSkill = SkillManifest(
    id = "run-detekt",
    description = "Run Detekt static analysis for Kotlin",
    command = SkillCommand(
        program = "./gradlew",
        args = listOf("detekt"),
        cwd = "subproject",
        env = mapOf("CI" to "true"),
    ),
    triggers = listOf(
        "User asks to run detekt",
        "User asks for static analysis on Kotlin code",
    ),
    prerequisites = listOf(
        "Requires Detekt to be configured in the project.",
    ),
    instructions = "After running, summarize findings by severity.",
    timeoutSec = 600,
    outputs = SkillOutputs(
        files = listOf(
            SkillOutputFile(
                path = "build/reports/detekt/detekt.html",
                description = "HTML report with detailed findings",
            ),
        ),
    ),
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
        author = "Test Author",
        created = "2025-01-01",
        tags = setOf("detekt", "kotlin"),
    ),
)

val expectedCommandSkill =
    """
    # run-detekt

    Run Detekt static analysis for Kotlin

    ## When to use

    - User asks to run detekt
    - User asks for static analysis on Kotlin code

    ## Prerequisites

    - Requires Detekt to be configured in the project.

    ## How to use

    Run the following command:

    ```bash
    ./gradlew detekt
    ```

    **Working directory:** `subproject`

    **Environment variables:**

    - `CI=true`

    **Timeout:** 600 seconds

    ## Output files

    - `build/reports/detekt/detekt.html`: HTML report with detailed findings

    ## Instructions

    After running, summarize findings by severity.

    """.trimIndent()

val mcpToolSkill = SkillManifest(
    id = "search-repo",
    description = "Search repository for code patterns or text",
    mcpTool = "filesystem:search",
    inputs = listOf(
        SkillInput(name = "pattern", type = "string", required = true, description = "Search pattern"),
        SkillInput(name = "path", type = "directory", required = false, description = "Directory to search in"),
    ),
    triggers = listOf("User asks to search the codebase"),
    prerequisites = listOf("Use ripgrep if available."),
    timeoutSec = 60,
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
        tags = setOf("search", "filesystem"),
    ),
)

val expectedMcpToolSkill =
    """
    # search-repo

    Search repository for code patterns or text

    ## When to use

    - User asks to search the codebase

    ## Prerequisites

    - Use ripgrep if available.

    ## How to use

    This skill uses the MCP tool: `filesystem:search`

    ### Inputs

    - `pattern` (string) (required): Search pattern
    - `path` (directory): Directory to search in

    **Timeout:** 60 seconds

    """.trimIndent()

val fragment = FragmentManifest(
    id = "test-fragment",
    description =
        """
        Multiline
        description
        """.trimIndent(),
    content =
        """
        This is reference material.
        It can contain any free-form content.
        """.trimIndent(),
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
        author = "Test Author",
        created = "2026-04-03",
    ),
)

val expectedFragment =
    """
    # test-fragment

    Multiline
    description

    ## Content

    This is reference material.
    It can contain any free-form content.

    """.trimIndent()

val fragments = mapOf(
    fragment.id to fragment,
)

val agentWithFragments = AgentManifest(
    id = "test-agent-with-fragments",
    description = "Agent with fragments",
    rulesets = listOf("test-ruleset"),
    fragments = listOf("test-fragment"),
    rules = listOf("rule1"),
    persona = "Test persona",
    prompt = "Test prompt",
    constraints = emptyList(),
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
    ),
)

val expectedAgentWithFragments =
    """
    # test-agent-with-fragments

    Agent with fragments

    ## Persona

    Test persona

    ## Rules

    - Rule number one.
    - Rule number two.
    - Rule number three.
    - rule1

    ## Prompt

    Test prompt

    ## Fragments

    ### test-fragment

    This is reference material.
    It can contain any free-form content.

    """.trimIndent()

val promptWithFragments = PromptManifest(
    id = "test-prompt-with-fragments",
    description = "Prompt with fragments",
    rulesets = listOf("test-ruleset"),
    fragments = listOf("test-fragment"),
    rules = listOf("rule1"),
    content = "Test content",
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
    ),
)

val expectedPromptWithFragments =
    """
    # test-prompt-with-fragments

    Prompt with fragments

    ## Rules

    - Rule number one.
    - Rule number two.
    - Rule number three.
    - rule1

    ## Prompt

    Test content

    ## Fragments

    ### test-fragment

    This is reference material.
    It can contain any free-form content.

    """.trimIndent()
