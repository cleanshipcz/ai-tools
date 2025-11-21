# create-prompt

Clarify prompt requirements and create a simple prompt.yml manifest

## Variables

- `{{user_description}}` (required): Brief description of what the prompt should do

## Prompt

The user wants to create a prompt based on this description:

{{user_description}}

**Your job is to clarify and translate:**

1. **Ask clarifying questions:**
   - What is the single, specific task this prompt performs?
   - What inputs does it need? (these become variables)
   - Are the inputs required or optional?
   - What should the output look like? (markdown, code, json, yaml, text)
   - What category? (refactor, qa, docs, planning, tickets, manifests)
   - Any specific constraints or rules it should follow?

2. **Suggest structure:**
   - Propose a kebab-case ID
   - Identify 2-3 key variables with snake_case names
   - Recommend appropriate category

3. **Create simple prompt.yml with:**
   - id, version (1.0.0), description (1 sentence)
   - tags: [3-5 relevant tags]
   - variables: [list with name, required, description]
   - rules: [3-5 key constraints]
   - content: "Clear, actionable prompt template with {{variables}}"
   - outputs: { format: X }
   - metadata: author, dates

**Keep it atomic, allow complexity:**
- ONE clear purpose per prompt
- Content: Recommend 5-15 lines; allow more for complex prompts
- Rules: Recommend 3-5 bullet points; allow more if needed
- Simple prompts: ~40-60 lines; Complex prompts: as needed

**Example conversation:**

User: "Extract a method from selected code"

You: "Got it! Questions:
- What input do we need? (the code block, the language?)
- Should it auto-name the method or let user specify?
- Any rules? (preserve behavior, add docstring, etc.)"

User: "Code block and language as input, user specifies method name, preserve behavior exactly"

You: "Perfect! I'll create 'extract-method' with:
- Variables: code_block (required), language (required), method_name (required)
- Rules: Preserve behavior, extract to pure function, add type hints
- Output: code
Sound good?"

User: "Yes"

You: [Creates simple ~50 line prompt.yml]

**DO NOT:**
- Write elaborate multi-paragraph prompts
- Include extensive examples in the content
- Create complex conditional logic
- Try to solve the problem - just define the task

