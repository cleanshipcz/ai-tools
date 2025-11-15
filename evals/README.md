# Evaluation Framework

The evaluation framework validates AI agent behavior through automated tests - ensuring prompts, agents, and rulepacks work as expected before deployment.

## 📁 What's Here

### Eval Suites (`suites/`)

- **basic-reasoning.suite.yaml** - Tests for logical reasoning and problem-solving
- **code-generation.suite.yaml** - Validates code quality and correctness
- **prompt-adherence.suite.yaml** - Checks if agents follow instructions

### Datasets (`datasets/`)

- **reasoning-problems.json** - Sample reasoning questions
- **code-challenges.json** - Programming tasks with expected outputs
- **instruction-tests.json** - Instruction-following scenarios

## 🎯 Purpose

Evaluation suites **automatically test AI agents** to:

- ✅ Validate prompt behavior
- 🐛 Catch regressions early
- 📊 Measure performance
- 🔒 Ensure safety & alignment

**Without Evals:** Manual testing, inconsistent quality  
**With Evals:** Automated validation, consistent performance

## 🔧 Running Evaluations

```bash
# Run all evaluations
npm run eval

# Run specific suite
npm run eval -- --suite basic-reasoning

# Run with specific agent
npm run eval -- --agent code-reviewer

# Verbose output
npm run eval -- --verbose
```

## 📝 Creating Eval Suites

### Basic Eval Suite

```yaml
# evals/suites/my-tests.suite.yaml
name: my-tests
description: Tests for my custom agent
version: 1.0.0

# Test configuration
config:
  timeout: 30000 # 30 seconds per test
  retries: 2 # Retry failed tests
  budget:
    max_tokens: 10000
    max_cost: 0.50 # USD

# Test cases
tests:
  - id: test-1
    name: Basic reasoning
    input: 'What is 2 + 2?'
    expected:
      contains: '4'
    checks:
      - type: exact-match
        value: '4'

  - id: test-2
    name: Code generation
    input: 'Write a function to reverse a string'
    expected:
      contains:
        - 'function'
        - 'reverse'
    checks:
      - type: contains-all
        values:
          - 'function'
          - 'reverse'
      - type: no-errors
      - type: valid-syntax
        language: javascript

  - id: test-3
    name: Instruction following
    input: 'List 3 colors, one per line'
    expected:
      format: list
      count: 3
    checks:
      - type: line-count
        value: 3
      - type: format
        value: list
```

### Advanced: Multi-Agent Testing

```yaml
# evals/suites/agent-comparison.suite.yaml
name: agent-comparison
description: Compare multiple agents on same tasks

agents:
  - code-reviewer
  - code-generator
  - code-refactorer

tests:
  - id: review-quality
    input: |
      Review this code:
      function add(a,b){return a+b}
    checks:
      - type: contains-any
        values:
          - 'semicolon'
          - 'formatting'
          - 'style'
    # All agents will be tested with this input
```

## 📊 Understanding Results

### Example Output

```
Running eval suite: basic-reasoning
Agent: code-reviewer
Budget: $0.50 max

✅ test-1: Basic reasoning (0.5s)
   Input: "What is 2 + 2?"
   Output: "4"
   Checks: 1/1 passed

❌ test-2: Code generation (2.3s)
   Input: "Write a function to reverse a string"
   Output: "Here's a reverse function..."
   Checks: 1/2 passed
   Failed: valid-syntax (syntax error on line 3)

Summary:
  Passed: 1/2 (50%)
  Failed: 1/2 (50%)
  Total time: 2.8s
  Tokens used: 347
  Cost: $0.02
```

### Result Codes

- ✅ **Passed** - All checks successful
- ❌ **Failed** - One or more checks failed
- ⚠️ **Warning** - Test passed but exceeded budget
- ⏱️ **Timeout** - Test exceeded time limit
- 🔁 **Retry** - Test retried after failure

## 🧪 Check Types

### Text Matching

```yaml
checks:
  # Exact match
  - type: exact-match
    value: 'Expected output'

  # Contains substring
  - type: contains
    value: 'keyword'

  # Contains all
  - type: contains-all
    values:
      - 'word1'
      - 'word2'

  # Contains any
  - type: contains-any
    values:
      - 'option1'
      - 'option2'

  # Regex match
  - type: regex
    pattern: "\\d{3}-\\d{4}"
```

### Code Validation

```yaml
checks:
  # Syntax validation
  - type: valid-syntax
    language: javascript

  # No runtime errors
  - type: no-errors

  # Code runs successfully
  - type: executes
    language: python
    timeout: 5000

  # Expected output
  - type: output-equals
    value: '42'
```

### Format Checks

```yaml
checks:
  # Line count
  - type: line-count
    value: 5

  # Word count
  - type: word-count
    min: 10
    max: 50

  # Format type
  - type: format
    value: json # or yaml, markdown, list

  # Valid JSON
  - type: valid-json

  # Valid YAML
  - type: valid-yaml
```

### Performance Checks

```yaml
checks:
  # Response time
  - type: response-time
    max: 2000 # milliseconds

  # Token usage
  - type: token-count
    max: 1000

  # Cost limit
  - type: cost
    max: 0.10 # USD
```

## 📂 Dataset Management

### Creating Datasets

```json
// evals/datasets/my-tests.json
{
  "name": "my-tests",
  "description": "Test cases for feature X",
  "version": "1.0.0",
  "tests": [
    {
      "id": "test-1",
      "input": "Test input",
      "expected": "Expected output",
      "metadata": {
        "category": "reasoning",
        "difficulty": "easy"
      }
    }
  ]
}
```

### Using Datasets in Suites

```yaml
# evals/suites/my-suite.suite.yaml
name: my-suite
dataset: my-tests # References datasets/my-tests.json

# Override checks for all tests
checks:
  - type: contains
    value: expected
  - type: response-time
    max: 3000
```

## 🎯 Best Practices

### 1. Test Early and Often

```bash
# Before commit
npm run validate
npm run eval

# In CI pipeline
- run: npm run ci
```

### 2. Isolate Test Cases

```yaml
# Good: One concept per test
- id: test-addition
  input: 'What is 2 + 2?'
- id: test-subtraction
  input: 'What is 5 - 3?'

# Bad: Multiple concepts
- id: test-math
  input: 'What is 2 + 2 and 5 - 3?'
```

### 3. Use Realistic Inputs

```yaml
# Good: Real-world scenario
- input: |
    Review this pull request:
    - Added error handling
    - Fixed typo in comment

# Bad: Toy example
- input: 'Review code'
```

### 4. Set Appropriate Budgets

```yaml
config:
  timeout: 10000 # 10s for simple tasks
  budget:
    max_tokens: 500 # Small for simple outputs
    max_cost: 0.01 # Penny per test

# For complex tasks:
config:
  timeout: 60000 # 1 minute
  budget:
    max_tokens: 4000
    max_cost: 0.25
```

### 5. Version Your Eval Suites

```yaml
name: my-suite
version: 2.1.0 # Track changes
changelog:
  - 2.1.0: Added code execution checks
  - 2.0.0: Rewrote dataset with realistic examples
  - 1.0.0: Initial version
```

## 🔗 Integration with CI/CD

### GitHub Actions

```yaml
# .github/workflows/test.yml
name: Test
on: [push, pull_request]

jobs:
  eval:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
      - run: npm ci
      - run: npm run validate
      - run: npm run eval
      - name: Upload results
        uses: actions/upload-artifact@v3
        with:
          name: eval-results
          path: eval-results.json
```

### Pre-commit Hook

```bash
#!/bin/bash
# .git/hooks/pre-commit

echo "Running evaluations..."
npm run eval -- --quick

if [ $? -ne 0 ]; then
  echo "❌ Evaluations failed"
  exit 1
fi
```

## 📊 Evaluation Metrics

### Pass Rate

```
Pass Rate = (Passed Tests / Total Tests) × 100%

Target: >95% for production agents
```

### Cost Efficiency

```
Cost per Test = Total Cost / Number of Tests

Target: <$0.05 per test for standard suites
```

### Performance

```
Avg Response Time = Total Time / Number of Tests

Target: <3s per test for interactive agents
```

## 🐛 Troubleshooting

### Tests Failing Intermittently

```yaml
# Increase retries
config:
  retries: 3

# Increase timeout
config:
  timeout: 60000

# Make checks less strict
checks:
  - type: contains # Instead of exact-match
```

### Budget Exceeded

```yaml
# Reduce token limit
config:
  budget:
    max_tokens: 2000 # Lower limit

# Optimize prompts
# See prompts/README.md for tips
```

### Invalid Syntax Checks Failing

```yaml
# Add language
checks:
  - type: valid-syntax
    language: javascript # Specify language

# Or use contains instead
checks:
  - type: contains
    value: "function"
```

## 🔗 Related

- [Prompts](../prompts/README.md) - Test prompt behavior
- [Agents](../agents/README.md) - Test agent performance
- [CI/CD](../.github/workflows/) - Automated testing

## 💡 Example: Complete Eval Suite

```yaml
# evals/suites/code-reviewer.suite.yaml
name: code-reviewer
description: Tests for code review agent
version: 1.0.0

config:
  timeout: 15000
  retries: 2
  budget:
    max_tokens: 2000
    max_cost: 0.10

tests:
  - id: detect-bugs
    name: Detect obvious bugs
    input: |
      Review this code:
      function divide(a, b) {
        return a / b;
      }
    expected:
      contains:
        - 'division by zero'
        - 'check'
    checks:
      - type: contains-any
        values:
          - 'zero'
          - 'undefined'
          - 'NaN'

  - id: suggest-improvements
    name: Suggest code improvements
    input: |
      Review this code:
      var x=1;var y=2;console.log(x+y);
    checks:
      - type: contains-any
        values:
          - 'const'
          - 'let'
          - 'formatting'
          - 'semicolon'

  - id: positive-feedback
    name: Give positive feedback on good code
    input: |
      Review this code:
      function add(a: number, b: number): number {
        return a + b;
      }
    checks:
      - type: contains-any
        values:
          - 'good'
          - 'clean'
          - 'well-written'
          - 'looks good'
```

## 📚 Further Reading

- [Main README](../README.md) - Full project documentation
- [Testing Guide](../docs/TESTING.md) - Testing best practices
- [CI/CD Setup](../docs/CI_CD.md) - Continuous integration
