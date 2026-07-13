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
import cz.cleanship.aitools.engine.models.SkillFile
import cz.cleanship.aitools.engine.models.SkillManifest
import cz.cleanship.aitools.engine.models.SkillSection
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

val textOnlySkill = SkillManifest(
    id = "run-pytest",
    description = "Run Python tests with pytest",
    sections = listOf(
        SkillSection.TextSection(
            text =
                """
                Run `pytest -v --tb=short` in the project directory.
                After running, summarize results: total, passed, failed, skipped.
                If there are failures, show the failing test names and error messages.
                """.trimIndent(),
        ),
    ),
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
        author = "Test Author",
        created = "2025-01-01",
        tags = setOf("pytest", "python", "testing"),
    ),
)

val expectedTextOnlySkill =
    """
    # run-pytest

    Run Python tests with pytest

    Run `pytest -v --tb=short` in the project directory.
    After running, summarize results: total, passed, failed, skipped.
    If there are failures, show the failing test names and error messages.

    """.trimIndent()

val skillWithRuleset = SkillManifest(
    id = "skill-with-ruleset",
    description = "Skill that includes a ruleset",
    sections = listOf(
        SkillSection.TextSection(text = "## Rules"),
        SkillSection.RulesetSection(ruleset = "test-ruleset"),
    ),
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
    ),
)

val expectedSkillWithRuleset =
    """
    # skill-with-ruleset

    Skill that includes a ruleset

    ## Rules

    - Rule number one.
    - Rule number two.
    - Rule number three.

    """.trimIndent()

val skillWithFragment = SkillManifest(
    id = "skill-with-fragment",
    description = "Skill that includes a fragment",
    sections = listOf(
        SkillSection.TextSection(text = "## Reference Material"),
        SkillSection.FragmentSection(fragment = "test-fragment"),
    ),
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
    ),
)

val expectedSkillWithFragment =
    """
    # skill-with-fragment

    Skill that includes a fragment

    ## Reference Material

    This is reference material.
    It can contain any free-form content.

    """.trimIndent()

val skillWithMixedSections = SkillManifest(
    id = "skill-with-mixed-sections",
    description = "Skill with all section types",
    sections = listOf(
        SkillSection.TextSection(
            text =
                """
                Use this skill when creating documentation.

                ## Conventions
                """.trimIndent(),
        ),
        SkillSection.RulesetSection(ruleset = "test-ruleset"),
        SkillSection.TextSection(text = "## Reference Material"),
        SkillSection.FragmentSection(fragment = "test-fragment"),
        SkillSection.TextSection(text = "Choose the appropriate template based on the document type."),
    ),
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
    ),
)

val expectedSkillWithMixedSections =
    """
    # skill-with-mixed-sections

    Skill with all section types

    Use this skill when creating documentation.

    ## Conventions

    - Rule number one.
    - Rule number two.
    - Rule number three.

    ## Reference Material

    This is reference material.
    It can contain any free-form content.

    Choose the appropriate template based on the document type.

    """.trimIndent()

val skillWithFiles = SkillManifest(
    id = "skill-with-files",
    description = "Skill with companion files",
    sections = listOf(
        SkillSection.TextSection(text = "See templates/example.txt for the template."),
    ),
    files = listOf(
        SkillFile(source = "templates/example.txt", target = "templates/example.txt"),
        SkillFile(source = "/absolute/shared.txt", target = "references/shared.txt"),
    ),
    metadata = ManifestMetadata(
        version = Version("1.0.0"),
    ),
)

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
