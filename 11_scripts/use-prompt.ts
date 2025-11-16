#!/usr/bin/env tsx
/**
 * Interactive CLI tool to use prompts
 * Fills variables and outputs ready-to-paste text
 */

import fs from 'fs';
import yaml from 'js-yaml';
import { glob } from 'glob';
import readline from 'readline';

type AIModel =
  | 'claude-sonnet-4.5'
  | 'claude-sonnet-4'
  | 'claude-haiku-4.5'
  | 'gpt-5'
  | 'gpt-5.1'
  | 'gpt-5.1-codex-mini'
  | 'gpt-5.1-codex';

interface Prompt {
  id: string;
  version: string;
  description: string;
  model?: AIModel;
  variables?: Array<{
    name: string;
    required: boolean;
    description: string;
  }>;
  content: string;
}

async function usePrompt() {
  const promptId = process.argv[2];

  if (!promptId) {
    console.error('❌ Usage: npm run use-prompt <prompt-id>');
    console.error('\nAvailable prompts:');
    await listPrompts();
    process.exit(1);
  }

  // Find the prompt
  const promptFiles = await glob('03_prompts/**/*.yml', { ignore: '03_prompts/shared/**' });
  let targetFile: string | null = null;

  for (const file of promptFiles) {
    const content = fs.readFileSync(file, 'utf-8');
    const prompt = yaml.load(content) as Prompt;
    if (prompt.id === promptId) {
      targetFile = file;
      break;
    }
  }

  if (!targetFile) {
    console.error(`❌ Prompt "${promptId}" not found`);
    console.error('\nAvailable prompts:');
    await listPrompts();
    process.exit(1);
  }

  // Load the prompt
  const content = fs.readFileSync(targetFile, 'utf-8');
  const prompt = yaml.load(content) as Prompt;

  console.log(`\n📝 Using prompt: ${prompt.id}`);
  console.log(`📄 ${prompt.description}\n`);

  if (!prompt.variables || prompt.variables.length === 0) {
    console.log("✅ No variables needed. Here's your prompt:\n");
    console.log('─'.repeat(80));
    console.log(prompt.content);
    console.log('─'.repeat(80));
    return;
  }

  // Collect variable values
  const values: Record<string, string> = {};
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });

  console.log('Please provide values for the following variables:\n');

  for (const variable of prompt.variables) {
    const required = variable.required ? '(required)' : '(optional)';
    const value = await question(
      rl,
      `  ${variable.name} ${required} - ${variable.description}\n  → `
    );

    if (!value && variable.required) {
      console.error(`\n❌ Error: ${variable.name} is required`);
      rl.close();
      process.exit(1);
    }

    if (value) {
      values[variable.name] = value;
    }
  }

  rl.close();

  // Fill in the prompt
  let filledPrompt = prompt.content;
  for (const [key, value] of Object.entries(values)) {
    // Replace {{key}} with value
    const regex = new RegExp(`\\{\\{${key}\\}\\}`, 'g');
    filledPrompt = filledPrompt.replace(regex, value);

    // Handle Mustache optional sections: {{#key}}...{{/key}}
    if (value) {
      const sectionRegex = new RegExp(`\\{\\{#${key}\\}\\}([\\s\\S]*?)\\{\\{/${key}\\}\\}`, 'g');
      filledPrompt = filledPrompt.replace(sectionRegex, '$1');
    } else {
      const sectionRegex = new RegExp(`\\{\\{#${key}\\}\\}[\\s\\S]*?\\{\\{/${key}\\}\\}`, 'g');
      filledPrompt = filledPrompt.replace(sectionRegex, '');
    }
  }

  // Remove any remaining optional sections
  filledPrompt = filledPrompt.replace(/\{\{#\w+\}\}[\s\S]*?\{\{\/\w+\}\}/g, '');

  // Handle includes ({{> filename}})
  const includeRegex = /\{\{>\s*(\w+)\}\}/g;
  filledPrompt = filledPrompt.replace(includeRegex, (match, filename) => {
    const includePath = `prompts/shared/${filename}.md`;
    if (fs.existsSync(includePath)) {
      return fs.readFileSync(includePath, 'utf-8');
    }
    return match;
  });

  console.log('\n✅ Prompt ready! Copy everything below:\n');
  console.log('═'.repeat(80));
  console.log(filledPrompt.trim());
  console.log('═'.repeat(80));
  console.log('\n💡 Paste this into ChatGPT, Claude, or any LLM');
}

async function listPrompts() {
  const promptFiles = await glob('03_prompts/**/*.yml', { ignore: '03_prompts/shared/**' });
  const prompts: Array<{ id: string; category: string; description: string }> = [];

  for (const file of promptFiles) {
    try {
      const content = fs.readFileSync(file, 'utf-8');
      const prompt = yaml.load(content) as Prompt;
      const category = file.split('/')[1];
      prompts.push({ id: prompt.id, category, description: prompt.description });
    } catch (error) {
      // Skip invalid files
    }
  }

  // Group by category
  const byCategory: Record<string, typeof prompts> = {};
  prompts.forEach((p) => {
    if (!byCategory[p.category]) byCategory[p.category] = [];
    byCategory[p.category].push(p);
  });

  Object.entries(byCategory)
    .sort()
    .forEach(([category, items]) => {
      console.log(`\n  ${category.toUpperCase()}:`);
      items.forEach((item) => {
        console.log(`    • ${item.id.padEnd(25)} - ${item.description}`);
      });
    });

  console.log('');
}

function question(rl: readline.Interface, prompt: string): Promise<string> {
  return new Promise((resolve) => {
    rl.question(prompt, (answer) => {
      resolve(answer.trim());
    });
  });
}

// Run
usePrompt().catch((error) => {
  console.error('❌ Error:', error.message);
  process.exit(1);
});
