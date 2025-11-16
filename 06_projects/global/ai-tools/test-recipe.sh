#!/bin/bash
# Auto-generated recipe script: feature-delivery
# Description: Complete feature implementation workflow with build, review, refactor, and documentation cycles
# Tool: claude-code
# Generated: 2025-11-16T23:30:36.607Z

set -e  # Exit on error

# Function to load project context from project.yml
load_project_context() {
  if [ -f "project.yml" ]; then
    echo "📋 Loading project context from project.yml..."
    # Use the ai-tools project-context script to format the context
    if command -v npx &> /dev/null; then
      PROJECT_CONTEXT=$(npx tsx "/home/blaha/Documents/Projects/ai-tools/11_scripts/format-project-context.ts" 2>/dev/null || echo "")
    else
      PROJECT_CONTEXT=""
    fi
  else
    PROJECT_CONTEXT=""
  fi
}

# Load project context once at the start
load_project_context

# Setup recipe documents directory
RECIPE_DOCS_DIR=".recipe-docs"
mkdir -p "$RECIPE_DOCS_DIR"

# Setup logging
RECIPE_LOGS_DIR=".recipe-logs"
mkdir -p "$RECIPE_LOGS_DIR"
LOG_FILE="$RECIPE_LOGS_DIR/feature-delivery-$(date +%Y%m%d-%H%M%S).log"
echo "📝 Logging to: $LOG_FILE"
echo "📁 Documents: $RECIPE_DOCS_DIR"
echo ""

# Redirect all output to both console and log file
exec > >(tee -a "$LOG_FILE") 2>&1

# Variables
FEATURE_DESCRIPTION="${FEATURE_DESCRIPTION}"
ACCEPTANCE_CRITERIA="${ACCEPTANCE_CRITERIA}"

# Step 1: analyze
echo "▶️  Step 1/7: analyze (project-planner)"
echo "📄 Output will be saved to: .recipe-docs/analysis.md"
mkdir -p "$RECIPE_DOCS_DIR"
# Build system prompt with project context and recipe info
SYSTEM_PROMPT=""
if [ -n "$PROJECT_CONTEXT" ]; then
  SYSTEM_PROMPT="$PROJECT_CONTEXT\n\n---\n\n"
fi
SYSTEM_PROMPT="$SYSTEM_PROMPTRecipe: feature-delivery\nStep: 1/7 (analyze)"

echo "⚡ Executing with claude-code..."
RESPONSE=$(claude @project-planner \
  --append-system-prompt "$SYSTEM_PROMPT" \
  --permission-mode acceptEdits \
  --allowedTools "Bash(git diff *),Read,Write,Edit" \
  -p "Analyze the following feature request in detail:

Feature: ${FEATURE_DESCRIPTION}

Acceptance Criteria:
${ACCEPTANCE_CRITERIA}

Please analyze:
1. The relevant parts of the codebase that will be affected
2. Dependencies and integrations involved
3. Potential challenges and risks
4. Technical considerations (architecture, patterns, security, performance)
5. Required changes to existing code
6. New files or modules that need to be created

Output your analysis as a comprehensive document covering all these aspects.
\\n\\n---\\n\\n**IMPORTANT**: Save your complete response to the file: \\\`.recipe-docs/analysis.md\\\`\\n")
echo "$RESPONSE"
echo "Saving to: $RECIPE_DOCS_DIR/analysis.md"
echo "$RESPONSE" > "$RECIPE_DOCS_DIR/analysis.md"
echo "✓ Document saved: .recipe-docs/analysis.md"

# Step 2: plan
echo "▶️  Step 2/7: plan (project-planner)"
# Include reference documents from previous steps
if [ -f "$RECIPE_DOCS_DIR/analysis.md" ]; then
  echo "✓ Including: .recipe-docs/analysis.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/analysis.md")
else
  echo "⚠️  Document not found: .recipe-docs/analysis.md"
fi
echo "📄 Output will be saved to: .recipe-docs/plan.md"
mkdir -p "$RECIPE_DOCS_DIR"
# Build system prompt with project context and recipe info
SYSTEM_PROMPT=""
if [ -n "$PROJECT_CONTEXT" ]; then
  SYSTEM_PROMPT="$PROJECT_CONTEXT\n\n---\n\n"
fi
SYSTEM_PROMPT="$SYSTEM_PROMPTRecipe: feature-delivery\nStep: 2/7 (plan)"

echo "⚡ Executing with claude-code..."
RESPONSE=$(claude @project-planner \
  --append-system-prompt "$SYSTEM_PROMPT" \
  --permission-mode acceptEdits \
  --allowedTools "Bash(git diff *),Read,Write,Edit" \
  -p "Based on the analysis, create a detailed implementation plan for:

Feature: ${FEATURE_DESCRIPTION}

Create a step-by-step plan that includes:
1. Order of implementation (which files/modules to create/modify first)
2. Specific code changes needed in each file
3. Test cases to be written
4. Edge cases to handle
5. Documentation updates needed
6. Estimated complexity for each step

Output your plan as a structured, actionable document that can guide implementation.
\\n\\n---\\n\\n## Reference Documents (Context)\\n\\n### Document: \\\`.recipe-docs/analysis.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n**Please use the documents above as context for your work.**\\n\\n\\n\\n---\\n\\n**IMPORTANT**: Save your complete response to the file: \\\`.recipe-docs/plan.md\\\`\\n")
echo "$RESPONSE"
echo "Saving to: $RECIPE_DOCS_DIR/plan.md"
echo "$RESPONSE" > "$RECIPE_DOCS_DIR/plan.md"
echo "✓ Document saved: .recipe-docs/plan.md"

# Step 3: implement
echo "▶️  Step 3/7: implement (feature-builder)"
# Include reference documents from previous steps
if [ -f "$RECIPE_DOCS_DIR/analysis.md" ]; then
  echo "✓ Including: .recipe-docs/analysis.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/analysis.md")
else
  echo "⚠️  Document not found: .recipe-docs/analysis.md"
fi
if [ -f "$RECIPE_DOCS_DIR/plan.md" ]; then
  echo "✓ Including: .recipe-docs/plan.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/plan.md")
else
  echo "⚠️  Document not found: .recipe-docs/plan.md"
fi
# Build system prompt with project context and recipe info
SYSTEM_PROMPT=""
if [ -n "$PROJECT_CONTEXT" ]; then
  SYSTEM_PROMPT="$PROJECT_CONTEXT\n\n---\n\n"
fi
SYSTEM_PROMPT="$SYSTEM_PROMPTRecipe: feature-delivery\nStep: 3/7 (implement)"

echo "⚡ Executing with claude-code..."
RESPONSE=$(claude @feature-builder \
  --append-system-prompt "$SYSTEM_PROMPT" \
  --permission-mode acceptEdits \
  --allowedTools "Bash(git diff *),Read,Write,Edit" \
  -p "Implement the following feature according to the analysis and plan:

Feature: ${FEATURE_DESCRIPTION}

Acceptance Criteria:
${ACCEPTANCE_CRITERIA}

Follow the implementation plan and analysis documents carefully.
Implement the feature with:
- Clean, maintainable code
- Proper error handling
- Comprehensive tests
- Documentation for public APIs

Update the plan document as you progress to track what's been completed.
\\n\\n---\\n\\n## Reference Documents (Context)\\n\\n### Document: \\\`.recipe-docs/analysis.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n### Document: \\\`.recipe-docs/plan.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n**Please use the documents above as context for your work.**\\n\\n")
echo "$RESPONSE"

# Step 4: review
echo "▶️  Step 4/7: review (code-reviewer)"
# Include reference documents from previous steps
if [ -f "$RECIPE_DOCS_DIR/analysis.md" ]; then
  echo "✓ Including: .recipe-docs/analysis.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/analysis.md")
else
  echo "⚠️  Document not found: .recipe-docs/analysis.md"
fi
if [ -f "$RECIPE_DOCS_DIR/plan.md" ]; then
  echo "✓ Including: .recipe-docs/plan.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/plan.md")
else
  echo "⚠️  Document not found: .recipe-docs/plan.md"
fi
echo "📄 Output will be saved to: .recipe-docs/review.md"
mkdir -p "$RECIPE_DOCS_DIR"
# Build system prompt with project context and recipe info
SYSTEM_PROMPT=""
if [ -n "$PROJECT_CONTEXT" ]; then
  SYSTEM_PROMPT="$PROJECT_CONTEXT\n\n---\n\n"
fi
SYSTEM_PROMPT="$SYSTEM_PROMPTRecipe: feature-delivery\nStep: 4/7 (review)"

echo "⚡ Executing with claude-code..."
RESPONSE=$(claude @code-reviewer \
  --append-system-prompt "$SYSTEM_PROMPT" \
  --permission-mode acceptEdits \
  --allowedTools "Bash(git diff *),Read,Write,Edit" \
  -p "Review the changes made in the previous step against the plan and acceptance criteria, expect the highest quality.

Focus on:
- Adherence to the implementation plan
- Code quality and maintainability
- Test coverage and quality
- Security concerns
- Performance considerations
- Documentation completeness

Provide specific, actionable feedback.
\\n\\n---\\n\\n## Reference Documents (Context)\\n\\n### Document: \\\`.recipe-docs/analysis.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n### Document: \\\`.recipe-docs/plan.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n**Please use the documents above as context for your work.**\\n\\n\\n\\n---\\n\\n**IMPORTANT**: Save your complete response to the file: \\\`.recipe-docs/review.md\\\`\\n")
echo "$RESPONSE"
echo "Saving to: $RECIPE_DOCS_DIR/review.md"
echo "$RESPONSE" > "$RECIPE_DOCS_DIR/review.md"
echo "✓ Document saved: .recipe-docs/review.md"

# Step 5: refactor
echo "▶️  Step 5/7: refactor (refactoring-specialist)"
# Include reference documents from previous steps
if [ -f "$RECIPE_DOCS_DIR/analysis.md" ]; then
  echo "✓ Including: .recipe-docs/analysis.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/analysis.md")
else
  echo "⚠️  Document not found: .recipe-docs/analysis.md"
fi
if [ -f "$RECIPE_DOCS_DIR/plan.md" ]; then
  echo "✓ Including: .recipe-docs/plan.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/plan.md")
else
  echo "⚠️  Document not found: .recipe-docs/plan.md"
fi
if [ -f "$RECIPE_DOCS_DIR/review.md" ]; then
  echo "✓ Including: .recipe-docs/review.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/review.md")
else
  echo "⚠️  Document not found: .recipe-docs/review.md"
fi
# Build system prompt with project context and recipe info
SYSTEM_PROMPT=""
if [ -n "$PROJECT_CONTEXT" ]; then
  SYSTEM_PROMPT="$PROJECT_CONTEXT\n\n---\n\n"
fi
SYSTEM_PROMPT="$SYSTEM_PROMPTRecipe: feature-delivery\nStep: 5/7 (refactor)"

echo "⚡ Executing with claude-code..."
RESPONSE=$(claude @refactoring-specialist \
  --append-system-prompt "$SYSTEM_PROMPT" \
  --permission-mode acceptEdits \
  --allowedTools "Bash(git diff *),Read,Write,Edit" \
  -p "Address the review feedback from the previous step.

Please:
- Fix any issues identified
- Improve code quality as suggested
- Add missing tests or documentation
- Ensure all acceptance criteria are met
- Stay aligned with the original plan

Update the review document with the changes made.
\\n\\n---\\n\\n## Reference Documents (Context)\\n\\n### Document: \\\`.recipe-docs/analysis.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n### Document: \\\`.recipe-docs/plan.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n### Document: \\\`.recipe-docs/review.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n**Please use the documents above as context for your work.**\\n\\n")
echo "$RESPONSE"

# Step 6: quality-check
echo "▶️  Step 6/7: quality-check (code-reviewer)"
# Include reference documents from previous steps
if [ -f "$RECIPE_DOCS_DIR/analysis.md" ]; then
  echo "✓ Including: .recipe-docs/analysis.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/analysis.md")
else
  echo "⚠️  Document not found: .recipe-docs/analysis.md"
fi
if [ -f "$RECIPE_DOCS_DIR/plan.md" ]; then
  echo "✓ Including: .recipe-docs/plan.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/plan.md")
else
  echo "⚠️  Document not found: .recipe-docs/plan.md"
fi
if [ -f "$RECIPE_DOCS_DIR/review.md" ]; then
  echo "✓ Including: .recipe-docs/review.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/review.md")
else
  echo "⚠️  Document not found: .recipe-docs/review.md"
fi
# Build system prompt with project context and recipe info
SYSTEM_PROMPT=""
if [ -n "$PROJECT_CONTEXT" ]; then
  SYSTEM_PROMPT="$PROJECT_CONTEXT\n\n---\n\n"
fi
SYSTEM_PROMPT="$SYSTEM_PROMPTRecipe: feature-delivery\nStep: 6/7 (quality-check)"

echo "⚡ Executing with claude-code..."
RESPONSE=$(claude @code-reviewer \
  --append-system-prompt "$SYSTEM_PROMPT" \
  --permission-mode acceptEdits \
  --allowedTools "Bash(git diff *),Read,Write,Edit" \
  -p "Perform a final quality check on the implementation.

Verify:
- All review feedback has been addressed
- Tests pass and cover edge cases
- Code follows project conventions
- Documentation is complete
- Acceptance criteria are fully met

Respond with \"APPROVED\" if quality standards are met, or provide remaining issues.
\\n\\n---\\n\\n## Reference Documents (Context)\\n\\n### Document: \\\`.recipe-docs/analysis.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n### Document: \\\`.recipe-docs/plan.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n### Document: \\\`.recipe-docs/review.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n**Please use the documents above as context for your work.**\\n\\n")
echo "$RESPONSE"

# Step 7: document
echo "▶️  Step 7/7: document (documentation-writer)"
# Include reference documents from previous steps
if [ -f "$RECIPE_DOCS_DIR/analysis.md" ]; then
  echo "✓ Including: .recipe-docs/analysis.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/analysis.md")
else
  echo "⚠️  Document not found: .recipe-docs/analysis.md"
fi
if [ -f "$RECIPE_DOCS_DIR/plan.md" ]; then
  echo "✓ Including: .recipe-docs/plan.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/plan.md")
else
  echo "⚠️  Document not found: .recipe-docs/plan.md"
fi
if [ -f "$RECIPE_DOCS_DIR/review.md" ]; then
  echo "✓ Including: .recipe-docs/review.md"
  DOC_CONTENT=$(cat "$RECIPE_DOCS_DIR/review.md")
else
  echo "⚠️  Document not found: .recipe-docs/review.md"
fi
# Build system prompt with project context and recipe info
SYSTEM_PROMPT=""
if [ -n "$PROJECT_CONTEXT" ]; then
  SYSTEM_PROMPT="$PROJECT_CONTEXT\n\n---\n\n"
fi
SYSTEM_PROMPT="$SYSTEM_PROMPTRecipe: feature-delivery\nStep: 7/7 (document)"

echo "⚡ Executing with claude-code..."
RESPONSE=$(claude @documentation-writer \
  --append-system-prompt "$SYSTEM_PROMPT" \
  --permission-mode acceptEdits \
  --allowedTools "Bash(git diff *),Read,Write,Edit" \
  -p "Update project documentation to reflect the new feature.

Please update:
- README.md with feature description
- CHANGELOG.md with version entry
- Any relevant architecture docs

Use all the analysis, plan, and review documents as context for what was implemented.
\\n\\n---\\n\\n## Reference Documents (Context)\\n\\n### Document: \\\`.recipe-docs/analysis.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n### Document: \\\`.recipe-docs/plan.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n### Document: \\\`.recipe-docs/review.md\\\`\\n\\n${DOC_CONTENT}\\n\\n---\\n\\n**Please use the documents above as context for your work.**\\n\\n")
echo "$RESPONSE"

echo "✅ Recipe completed!"
echo "📁 All documents saved to: $RECIPE_DOCS_DIR"
