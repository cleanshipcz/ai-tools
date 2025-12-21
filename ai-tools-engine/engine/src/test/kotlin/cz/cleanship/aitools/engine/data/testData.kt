package cz.cleanship.aitools.engine.data

import cz.cleanship.aitools.engine.models.AgentManifest
import cz.cleanship.aitools.engine.models.ManifestMetadata
import cz.cleanship.aitools.engine.models.PromptManifest
import cz.cleanship.aitools.engine.models.PromptOutput
import cz.cleanship.aitools.engine.models.PromptVariable
import cz.cleanship.aitools.engine.models.RulepackManifest
import cz.cleanship.aitools.engine.models.Version


val rulepack = RulepackManifest(
    id = "test-rulepack",
    description = """
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

val expectedRulepack = """
    # test-rulepack
    
    Multiline
    description
    
    ## Rules
    
    - Rule number one.
    - Rule number two.
    - Rule number three.

""".trimIndent()

val rulepacks = mapOf(
    rulepack.id to rulepack,
)

val agent = AgentManifest(
    id = "test-agent",
    description = """
        Multiline
        description
    """.trimIndent(),
    rulepacks = listOf("test-rulepack"),
    persona = """
        Multiline
        persona
            """.trimIndent(),
    prompt = """
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

val expectedAgent = """
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
    
    ## Prompt
    
    Multiline
    system prompt
    
    ## Constraints
    
    - Constraint one.
    - Constraint two.
    
""".trimIndent()

val prompt = PromptManifest(
    id = "id#1",
    description = """
                Multiline
                description#1
            """.trimIndent(),
    variables = listOf(
        PromptVariable(
            name = "var1", required = true, description = """
                    Multiline
                    description#2
                """.trimIndent()
        ),
        PromptVariable(
            name = "var2", required = false, description = """
                    Multiline
                    description#3
                """.trimIndent()
        ),
    ),
    rules = listOf("rule1", "rule2"),
    content = """
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

val expectedPrompt = """
    # id#1
    
    Multiline
    description#1
    
    ## Variables
    
    - `{{var1}}` (required): Multiline
      description#2
    - `{{var2}}`: Multiline
      description#3
    
    ## Rules
    
    - rule1
    - rule2
    
    ## Prompt
    
    Multiline
    content#3
    
""".trimIndent()
