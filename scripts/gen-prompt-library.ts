#!/usr/bin/env tsx
/**
 * Generate a user-friendly prompt library in Markdown format
 * Output: PROMPT_LIBRARY.md - ready-to-copy prompts for ChatGPT, Claude, etc.
 */

import fs from 'fs';
import path from 'path';
import yaml from 'js-yaml';
import { glob } from 'glob';

interface Prompt {
  id: string;
  version: string;
  description: string;
  tags?: string[];
  variables?: Array<{
    name: string;
    required: boolean;
    description: string;
  }>;
  content: string;
  rules?: string[];
  outputs?: {
    format: string;
  };
}

async function generatePromptLibrary() {
  console.log('📚 Generating user-friendly prompt library...\n');

  // Find all prompt files
  const promptFiles = await glob('prompts/**/*.yml', { ignore: 'prompts/shared/**' });

  if (promptFiles.length === 0) {
    console.error('❌ No prompt files found');
    process.exit(1);
  }

  // Load and parse prompts
  const prompts: Prompt[] = [];
  for (const file of promptFiles) {
    try {
      const content = fs.readFileSync(file, 'utf-8');
      const prompt = yaml.load(content) as Prompt;
      prompts.push(prompt);
    } catch (error) {
      console.error(`⚠️  Failed to load ${file}:`, error);
    }
  }

  // Sort prompts by category (from path)
  const promptsByCategory = prompts.reduce(
    (acc, prompt) => {
      const file = promptFiles.find((f) => f.includes(prompt.id));
      const category = file ? path.dirname(file).split('/')[1] : 'other';
      if (!acc[category]) acc[category] = [];
      acc[category].push(prompt);
      return acc;
    },
    {} as Record<string, Prompt[]>
  );

  // Generate markdown
  let markdown = `# Prompt Library

> **User-Friendly Prompt Collection**  
> Copy-paste ready prompts for ChatGPT, Claude, and any LLM

This library contains ready-to-use prompts. Just copy the prompt text, replace the \`{{variables}}\` with your actual content, and paste into your favorite LLM.

**Last Updated:** ${new Date().toISOString().split('T')[0]}  
**Total Prompts:** ${prompts.length}

---

## Table of Contents

`;

  // Add TOC
  for (const category of Object.keys(promptsByCategory).sort()) {
    markdown += `- [${capitalizeFirst(category)}](#${category})\n`;
  }

  markdown += '\n---\n\n';

  // Generate sections by category
  for (const [category, categoryPrompts] of Object.entries(promptsByCategory).sort()) {
    markdown += `## ${capitalizeFirst(category)}\n\n`;

    for (const prompt of categoryPrompts.sort((a, b) => a.id.localeCompare(b.id))) {
      markdown += generatePromptSection(prompt);
    }
  }

  // Add usage guide at the end
  markdown += `---

## How to Use These Prompts

### Step 1: Choose a Prompt
Browse the categories above and find a prompt that fits your need.

### Step 2: Copy the Prompt Text
Copy everything in the "📋 Prompt Text" section.

### Step 3: Replace Variables
Look for \`{{variable_name}}\` placeholders and replace them with your actual content.

**Example:**
\`\`\`
Extract the following {{language}} code...
{{code}}
\`\`\`

Becomes:
\`\`\`
Extract the following Python code...
def hello():
    print("world")
\`\`\`

### Step 4: Paste into Your LLM
Paste the filled prompt into:
- ChatGPT (GPT-4, GPT-5, etc.)
- Claude.ai
- Any other LLM chat interface

### Step 5: Get Results!
The LLM will follow the prompt's instructions and give you the output.

---

## Need More Advanced Usage?

This library is for **quick, manual usage**. For more advanced scenarios:

- **Automate with tools:** See [README.md](README.md) for integrating with Windsurf, Cursor, etc.
- **Create custom prompts:** Edit YAML files in \`prompts/\` directory
- **Use programmatically:** Build adapters and use JSON output in scripts

---

Generated from ai-tools repository | [View Source](https://github.com/cleanshipcz/ai-tools)
`;

  // Write to file
  fs.writeFileSync('PROMPT_LIBRARY.md', markdown);
  console.log(`✅ Generated PROMPT_LIBRARY.md with ${prompts.length} prompts`);
}

function generatePromptSection(prompt: Prompt): string {
  let section = `### ${prompt.id}\n\n`;
  section += `**${prompt.description}**\n\n`;

  // Tags
  if (prompt.tags && prompt.tags.length > 0) {
    section += `🏷️ Tags: ${prompt.tags.map((t) => `\`${t}\``).join(', ')}\n\n`;
  }

  // Version
  section += `📦 Version: \`${prompt.version}\`\n\n`;

  // Variables
  if (prompt.variables && prompt.variables.length > 0) {
    section += `**Required Variables:**\n\n`;
    for (const v of prompt.variables) {
      const required = v.required ? '✅ Required' : '⚪ Optional';
      section += `- \`{{${v.name}}}\` - ${required} - ${v.description}\n`;
    }
    section += '\n';
  }

  // Rules
  if (prompt.rules && prompt.rules.length > 0) {
    section += `**Guidelines:**\n\n`;
    for (const rule of prompt.rules) {
      section += `- ${rule}\n`;
    }
    section += '\n';
  }

  // Output format
  if (prompt.outputs?.format) {
    section += `**Expected Output:** \`${prompt.outputs.format}\`\n\n`;
  }

  // The actual prompt text
  section += `<details>\n<summary>📋 <strong>Prompt Text (Click to Expand)</strong></summary>\n\n`;
  section += '```\n';
  section += prompt.content.trim();
  section += '\n```\n\n';
  section += '</details>\n\n';

  // Usage example
  section += `<details>\n<summary>💡 <strong>Usage Example</strong></summary>\n\n`;
  section += generateUsageExample(prompt);
  section += '</details>\n\n';

  section += '---\n\n';
  return section;
}

function generateUsageExample(prompt: Prompt): string {
  let example = '**How to use this prompt:**\n\n';
  example += '1. Copy the prompt text above\n';
  example += '2. Replace these variables:\n\n';

  if (prompt.variables && prompt.variables.length > 0) {
    for (const v of prompt.variables.filter((v) => v.required)) {
      example += `   - \`{{${v.name}}}\` → Your ${v.name}\n`;
    }
  } else {
    example += '   - No variables needed, use as-is\n';
  }

  example += '\n3. Paste into ChatGPT, Claude, or any LLM\n';
  example += '4. Review the output\n\n';

  return example;
}

function capitalizeFirst(str: string): string {
  return str.charAt(0).toUpperCase() + str.slice(1);
}

// Run
generatePromptLibrary().catch((error) => {
  console.error('❌ Failed to generate prompt library:', error);
  process.exit(1);
});
