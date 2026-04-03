# Recipe Document Flow - Implementation Guide

## Overview

Recipes now properly implement a **document-first workflow** where each step in a recipe workflow creates documents that serve as context for subsequent steps. This solves the original bug where steps would re-analyze instead of reusing context.

## How It Works

### Document Persistence

Documents are stored in a hidden `.recipe-docs/` folder in the project root:

```bash
project/
├── .recipe-docs/          # Hidden recipe documents folder
│   ├── analysis.md        # Initial analysis output
│   ├── plan.md            # Planning and strategy
│   ├── review.md          # Code review findings
│   ├── progress.md        # Implementation progress tracking
│   └── ...                # Other step outputs
├── src/
├── tests/
└── package.json
```

Each recipe run creates a fresh `.recipe-docs/` folder for debugging and auditing purposes.

### Step Flow Pattern

```text
Step 1: Analysis
├── Creates: .recipe-docs/analysis.md
└── Context: (none)

Step 2: Planning
├── Reads: .recipe-docs/analysis.md
├── Creates: .recipe-docs/plan.md
└── Context: analysis.md injected into task

Step 3: Implementation
├── Reads: .recipe-docs/analysis.md, .recipe-docs/plan.md
├── Updates: .recipe-docs/plan.md (track progress)
└── Context: both documents injected into task

Step 4: Review
├── Reads: .recipe-docs/analysis.md, .recipe-docs/plan.md
├── Creates: .recipe-docs/review.md
└── Context: both documents injected into task

Step 5+: Refinement/Iteration
├── Reads: all previous documents
├── Updates: relevant documents with progress
└── Context: all documents injected into task
```

## Recipe Configuration

### Step Properties for Document Flow

Each recipe step can now use these properties:

```yaml
steps:
  - id: step-id
    agent: agent-name
    task: 'Task description...'

    # Output document: step creates this file
    outputDocument: '.recipe-docs/analysis.md'

    # Include documents: step reads these as context
    includeDocuments:
      - '.recipe-docs/analysis.md'
      - '.recipe-docs/plan.md'

    # Wait for confirmation before proceeding
    waitForConfirmation: true

    # Continue in same conversation (true) or start fresh (false)
    continueConversation: false
```

### Document Injection Format

When a step has `includeDocuments`, they are automatically injected into the task as formatted context:

```text
Original task: "Plan the implementation..."

Injected task becomes:
"Plan the implementation...

---

## Reference Documents (Context)

### Document: `.recipe-docs/analysis.md`

[full content of analysis.md]

---

### Document: `.recipe-docs/plan.md`

[full content of plan.md]

---

**Please use the documents above as context for your work.**"
```

## Recipe Examples

### Feature Delivery Workflow

```yaml
feature-delivery:
  1. analyze
     → Creates: analysis.md

  2. plan
     → Reads: analysis.md
     → Creates: plan.md

  3. implement
     → Reads: analysis.md, plan.md
     → Updates: plan.md with progress

  4. review
     → Reads: analysis.md, plan.md
     → Creates: review.md

  5. refactor
     → Reads: analysis.md, plan.md, review.md
     → Updates: review.md with fixes

  6. quality-check
     → Reads: all three documents
     → Approves if ready, or loops back

  7. document
     → Reads: all documents
     → Updates: project documentation
```

### Bug Fix Workflow

```yaml
bug-fix-workflow:
  1. analyze-bug
     → Creates: bug-analysis.md

  2. plan-fix
     → Reads: bug-analysis.md
     → Creates: fix-plan.md

  3. investigate
     → Reads: bug-analysis.md, fix-plan.md
     → Creates: investigation.md

  4. implement-fix
     → Reads: all three
     → Updates: investigation.md with progress

  5. test-verification
     → Reads: all documents
     → Updates: investigation.md with test results

  6. review-fix
     → Reads: all documents
     → Creates: review-feedback.md

  7. update-docs
     → Reads: all documents (conditional on APPROVED)
```

### Code Review Workflow

```yaml
code-review-cycle:
  1. analyze-changes
     → Creates: changes-analysis.md

  2. create-review-checklist
     → Reads: changes-analysis.md
     → Creates: review-checklist.md

  3. initial-review
     → Reads: both above
     → Creates: review-findings.md

  4. apply-fixes (LOOP)
     → Reads: all three
     → Updates: review-findings.md with progress

  5. verify-fixes (LOOP)
     → Reads: all documents
     → PASS/CONTINUE decision
```

## Runner Implementation Details

### Document Loading (`loadDocuments()`)

Before each step executes, the runner:

1. Reads all `.md` files from `.recipe-docs/` directory
2. Caches them in memory with path as key
3. Checks for missing documents and warns user

### Document Injection (`executeStep()`)

For each step:

1. Loads documents from disk
2. Interpolates variables in task
3. Appends reference documents section to task
4. Displays formatted task to user
5. Executes with tool (Claude, Copilot, Cursor)
6. Saves any outputDocument after tool execution

### Document Saving (`saveDocument()`)

After tool execution:

1. Prompts user to save output document
2. Reads document from disk
3. Caches in memory for subsequent steps
4. Reports success/failure

## Key Features

### Persistent Context

Documents survive across all steps in a workflow, eliminating re-analysis.

### Incremental Updates

Steps can read existing documents and update them with new information (e.g., tracking progress).

### Debugging

All documents are stored in `.recipe-docs/` for audit trail and debugging.

### Flexible Flow

Steps can include multiple documents or none, depending on workflow needs.

### Clear Communication

Document inclusion in tasks is clearly marked with "Reference Documents" section.

## Best Practices

### For Recipe Authors

1. **Start with Analysis**

   ```yaml
   - id: analyze
     agent: specialist
     task: 'Analyze the problem...'
     outputDocument: '.recipe-docs/analysis.md'
   ```

2. **Build on Previous Steps**

   ```yaml
   - id: plan
     includeDocuments:
       - '.recipe-docs/analysis.md'
     outputDocument: '.recipe-docs/plan.md'
   ```

3. **Update Documents with Progress**

   ```yaml
   - id: implement
     includeDocuments:
       - '.recipe-docs/analysis.md'
       - '.recipe-docs/plan.md'
     task: 'Implement according to plan. Update plan.md with what you complete.'
   ```

4. **Loop Until Complete**

   ```yaml
   loop:
     steps:
       - review
       - refactor
     maxIterations: 5
     condition:
       type: user-decision
       prompt: 'Continue loop? (y/n): '
   ```

### For Users

1. **Save Documents in Right Location**

   When prompted, save outputs to the exact path shown: `.recipe-docs/analysis.md`

2. **Review Documents Between Steps**

   Between steps, you can review the generated documents to ensure they're good before continuing.

3. **Use Confirmation Prompts**

   For critical decisions, add `waitForConfirmation: true` to review documents before proceeding.

4. **Check Document Folder**

   All workflow documents are in `.recipe-docs/` for reference and debugging.

## Document Naming Conventions

Use clear, semantic names for documents:

- `analysis.md` - Initial analysis/investigation
- `plan.md` - Implementation or refactoring plan
- `design.md` - System design document
- `review.md` - Code review findings
- `progress.md` - Progress tracking during implementation
- `results.md` - Test or verification results
- `feedback.md` - Review or feedback summary
- `investigation.md` - Investigation findings
- `checklist.md` - Review or verification checklist

## Technical Changes

### run-recipe.ts

**Added:**

- `recipeDocsDir` property to track `.recipe-docs/` location
- `loadDocuments()` method to read documents from disk
- Enhanced `executeStep()` to load documents before execution
- Improved `saveDocument()` with better error handling

**Modified:**

- Step execution now loads documents from disk before each step
- Task context now includes reference documents section
- Document caching survives entire workflow execution

### Recipe Schemas

The `recipe.schema.json` already supported `includeDocuments` and `outputDocument`.
No schema changes needed - we're just using existing properties properly.

### Updated Recipes

- `feature-delivery.yml` - Uncommented all steps, added full document flow
- `bug-fix-workflow.yml` - Added outputDocument to steps, improved includeDocuments
- `code-review-cycle.yml` - Enhanced with document tracking
- `major-refactoring-cycle.yml` - Full document flow for iterative refactoring

## Troubleshooting

### Document Not Found Warning

```text
⚠️  Document not found: .recipe-docs/analysis.md
```

**Cause:** A previous step that should have created this document didn't complete.

**Solution:**

1. Check that previous step created the document
2. Verify document name matches exactly
3. Re-run from beginning if needed

### Document Not Saved After Step

```text
⚠️  Could not read document: .recipe-docs/plan.md
```

**Cause:** User didn't save the document, or saved to wrong location.

**Solution:**

1. Save document to exact path shown: `.recipe-docs/plan.md`
2. Verify document was created in project root
3. Restart recipe if necessary

## Future Enhancements

Possible improvements for future versions:

1. **Auto-save Documents** - Automatically extract and save documents from AI responses
2. **Document Versioning** - Keep historical versions (analysis-v1.md, analysis-v2.md)
3. **Document Editing** - Allow users to edit documents between steps
4. **Template Documents** - Pre-populated document templates for each step
5. **Document Validation** - Validate document structure/content before proceeding
6. **Parallel Steps** - Execute independent steps in parallel with separate documents
