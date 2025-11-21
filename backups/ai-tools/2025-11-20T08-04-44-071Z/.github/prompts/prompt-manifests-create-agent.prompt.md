# create-agent

Clarify agent requirements and create a simple agent.yml manifest

## Variables

- `{{user_description}}` (required): Brief description of what the agent should do

## Prompt

The user wants to create an agent based on this description:

{{user_description}}

**Your job is to clarify and translate:**

1. **Ask clarifying questions:**
   - What is this agent's main purpose? (in 1-2 sentences)
   - What domain does it work in? (coding, docs, security, planning, etc.)
   - What programming languages apply? (if coding-related)
   - Does it need any special capabilities? (git, filesystem, docker, etc.)
   - Should it be precise or creative? (helps set temperature)
   - Any specific tools/skills it needs? (search-repo, run-tests, etc.)
   - What should it NOT do? (constraints)

2. **Suggest structure:**
   - Propose a kebab-case ID
   - Suggest relevant rulepacks (base, coding-*, security, reviewer, etc.)
   - Recommend temperature (0.3 for precise, 0.7 for balanced, 1.0 for creative)

3. **Create simple agent.yml with:**
   - id, version (1.0.0), purpose (clear 1-2 sentence statement)
   - rulepacks: [base, ...relevant ones]
   - capabilities: [...if needed]
   - defaults: { temperature: X }
   - prompt: { system: "Brief persona describing role and approach" }
   - constraints: [3-5 key boundaries]
   - metadata: author, dates, tags

**Start simple, allow detail:**
- System prompt: Recommend 3-5 sentences; allow more for specialized agents
- Constraints: Recommend 3-5 bullet points; add more if domain requires it
- Simple agents: ~50-80 lines; Complex agents: as needed

**Example conversation:**

User: "I need an agent that reviews security issues"

You: "Got it! A few questions:
- Should it focus on code vulnerabilities, or also infrastructure/config?
- Any specific languages? (Python, TypeScript, etc.)
- Should it be strict (flag everything) or balanced?
- Does it need git access to check commit history?"

User: "Code vulns in TypeScript, strict approach, no git needed"

You: "Perfect! I'll create 'security-reviewer' with:
- Purpose: Review TypeScript code for security vulnerabilities
- Rulepacks: base, coding-typescript, security
- Temperature: 0.2 (very precise)
- Constraints: Never approve code with known CVEs, etc.
Sound good?"

User: "Yes"

You: [Creates simple ~60 line agent.yml]

