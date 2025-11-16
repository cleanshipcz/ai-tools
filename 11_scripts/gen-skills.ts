#!/usr/bin/env tsx
/**
 * Generate Anthropic-compatible SKILL.md files from YAML manifests
 *
 * Hybrid Architecture:
 * - Source: skills/*.yml (tool-agnostic, validated, composable)
 * - Output: adapters/claude-code/skills/<skill-name>/SKILL.md (native Anthropic format)
 *
 * Why hybrid?
 * - YAML source works across all tools (Windsurf, Cursor, Claude)
 * - Generated SKILL.md provides native Claude support with progressive disclosure
 * - Single source of truth for multi-tool deployment
 *
 * Generated SKILL.md format:
 * - YAML frontmatter (name, description)
 * - Markdown instructions with usage examples
 * - Troubleshooting guides
 * - Metadata footer
 *
 * Output: adapters/claude-code/skills/<skill-name>/SKILL.md
 */

import fs from 'fs';
import path from 'path';
import yaml from 'js-yaml';
import { glob } from 'glob';

interface Skill {
  id: string;
  version?: string;
  description: string;
  command?: {
    program: string;
    args?: string[];
    cwd?: string;
    env?: Record<string, string>;
  };
  mcp_tool?: string;
  constraints?: string[];
  timeout_sec?: number;
  retry?: {
    max_attempts: number;
    backoff_ms: number;
  };
  inputs?: Array<{
    name: string;
    type: string;
    required?: boolean;
    description?: string;
  }>;
  outputs?: {
    stdout?: boolean;
    stderr?: boolean;
    exit_code?: boolean;
    files?: string[];
  };
  tags?: string[];
  metadata?: {
    author?: string;
    created?: string;
    updated?: string;
  };
}

function generateSkillMarkdown(skill: Skill): string {
  const lines: string[] = [];

  // YAML frontmatter (required by Anthropic)
  lines.push('---');
  lines.push(`name: ${skill.id}`);
  lines.push(`description: ${skill.description}`);
  lines.push('---');
  lines.push('');

  // Title
  const title = skill.id
    .split('-')
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
  lines.push(`# ${title}`);
  lines.push('');

  // When to use this skill
  lines.push('## When to use this skill');
  lines.push('');
  if (skill.tags && skill.tags.length > 0) {
    lines.push('Use this skill when:');
    lines.push(`- Working with ${skill.tags.join(', ')}`);
    lines.push(`- User asks to "${skill.id.replace(/-/g, ' ')}"`);
  } else {
    lines.push(`Use this skill when: ${skill.description}`);
  }
  lines.push('');

  // Constraints/Prerequisites
  if (skill.constraints && skill.constraints.length > 0) {
    lines.push('## Prerequisites');
    lines.push('');
    for (const constraint of skill.constraints) {
      lines.push(`- ${constraint}`);
    }
    lines.push('');
  }

  // How to use
  if (skill.command) {
    lines.push('## How to use');
    lines.push('');

    // Show command
    const args = skill.command.args ? ` ${skill.command.args.join(' ')}` : '';
    lines.push('Run the following command:');
    lines.push('');
    lines.push('```bash');
    lines.push(`${skill.command.program}${args}`);
    lines.push('```');
    lines.push('');

    // Working directory
    if (skill.command.cwd && skill.command.cwd !== '.') {
      lines.push(`Working directory: \`${skill.command.cwd}\``);
      lines.push('');
    }

    // Environment variables
    if (skill.command.env && Object.keys(skill.command.env).length > 0) {
      lines.push('### Environment variables');
      lines.push('');
      for (const [key, value] of Object.entries(skill.command.env)) {
        lines.push(`- \`${key}\`: ${value}`);
      }
      lines.push('');
    }

    // Timeout
    if (skill.timeout_sec) {
      lines.push(`**Timeout:** ${skill.timeout_sec} seconds`);
      lines.push('');
    }

    // Retry configuration
    if (skill.retry) {
      lines.push('### Retry configuration');
      lines.push('');
      lines.push(`- Max attempts: ${skill.retry.max_attempts}`);
      lines.push(`- Backoff: ${skill.retry.backoff_ms}ms`);
      lines.push('');
    }
  } else if (skill.mcp_tool) {
    lines.push('## How to use');
    lines.push('');
    lines.push(
      `This skill uses the MCP tool: \`${skill.mcp_tool}\`. The tool will be invoked automatically when you use this skill.`
    );
    lines.push('');
  }

  // Inputs
  if (skill.inputs && skill.inputs.length > 0) {
    lines.push('## Inputs');
    lines.push('');
    for (const input of skill.inputs) {
      const required = input.required ? ' (required)' : ' (optional)';
      lines.push(`### \`${input.name}\`${required}`);
      lines.push('');
      lines.push(`- Type: \`${input.type}\``);
      if (input.description) {
        lines.push(`- Description: ${input.description}`);
      }
      lines.push('');
    }
  }

  // Understanding output
  if (skill.outputs) {
    lines.push('## Understanding the output');
    lines.push('');

    if (skill.outputs.exit_code) {
      lines.push('### Exit code');
      lines.push('');
      lines.push('- `0`: Success');
      lines.push('- Non-zero: Error occurred (check stderr for details)');
      lines.push('');
    }

    if (skill.outputs.stdout) {
      lines.push('### Standard output (stdout)');
      lines.push('');
      lines.push('Contains the main output of the command.');
      lines.push('');
    }

    if (skill.outputs.stderr) {
      lines.push('### Standard error (stderr)');
      lines.push('');
      lines.push('Contains error messages and warnings.');
      lines.push('');
    }

    if (skill.outputs.files && skill.outputs.files.length > 0) {
      lines.push('### Output files');
      lines.push('');
      lines.push('The following files will be generated:');
      lines.push('');
      for (const file of skill.outputs.files) {
        lines.push(`- \`${file}\``);
      }
      lines.push('');
    }
  }

  // Examples
  lines.push('## Examples');
  lines.push('');
  lines.push('### Basic usage');
  lines.push('');
  lines.push('```');
  lines.push(`User: "Can you ${skill.id.replace(/-/g, ' ')}?"`);
  if (skill.command) {
    const args = skill.command.args ? ` ${skill.command.args.join(' ')}` : '';
    lines.push(`Assistant: [Runs: ${skill.command.program}${args}]`);
  }
  lines.push('Assistant: [Interprets output and reports results]');
  lines.push('```');
  lines.push('');

  // Troubleshooting
  lines.push('## Troubleshooting');
  lines.push('');
  if (skill.command) {
    lines.push('### Command not found');
    lines.push('');
    lines.push(`Ensure \`${skill.command.program}\` is installed and available in the PATH.`);
    lines.push('');
  }

  if (skill.timeout_sec) {
    lines.push('### Timeout');
    lines.push('');
    lines.push(
      `If the command takes longer than ${skill.timeout_sec} seconds, it will be terminated. Consider:`
    );
    lines.push('- Breaking down the task into smaller steps');
    lines.push('- Running on a subset of files');
    lines.push('- Checking for performance issues');
    lines.push('');
  }

  // Metadata footer
  if (skill.metadata) {
    lines.push('---');
    lines.push('');
    if (skill.metadata.author) {
      lines.push(`**Author:** ${skill.metadata.author}`);
    }
    if (skill.version) {
      lines.push(`**Version:** ${skill.version}`);
    }
    if (skill.metadata.created) {
      lines.push(`**Created:** ${skill.metadata.created}`);
    }
    if (skill.metadata.updated) {
      lines.push(`**Updated:** ${skill.metadata.updated}`);
    }
  }

  return lines.join('\n');
}

async function generateSkills() {
  console.log('🛠️  Generating Anthropic-compatible SKILL.md files...\n');

  // Output directory
  const outputDir = path.join(process.cwd(), 'adapters', 'claude-code', 'skills');

  // Clean and create output directory
  if (fs.existsSync(outputDir)) {
    fs.rmSync(outputDir, { recursive: true, force: true });
  }
  fs.mkdirSync(outputDir, { recursive: true });

  // Find all skill YAML files
  const skillFiles = await glob('02_skills/**/*.yml', { ignore: '02_skills/shared/**' });

  if (skillFiles.length === 0) {
    console.error('❌ No skill files found');
    process.exit(1);
  }

  console.log(`Found ${skillFiles.length} skills\n`);

  let successCount = 0;
  let errorCount = 0;

  // Process each skill
  for (const file of skillFiles) {
    try {
      // Load YAML
      const content = fs.readFileSync(file, 'utf-8');
      const skill = yaml.load(content) as Skill;

      if (!skill.id || !skill.description) {
        console.error(`⚠️  ${file}: Missing required fields (id or description)`);
        errorCount++;
        continue;
      }

      // Generate markdown
      const markdown = generateSkillMarkdown(skill);

      // Create skill directory
      const skillDir = path.join(outputDir, skill.id);
      fs.mkdirSync(skillDir, { recursive: true });

      // Write SKILL.md
      const skillPath = path.join(skillDir, 'SKILL.md');
      fs.writeFileSync(skillPath, markdown, 'utf-8');

      console.log(`✅ ${skill.id}/SKILL.md`);
      successCount++;
    } catch (error) {
      console.error(`❌ Failed to process ${file}:`, error);
      errorCount++;
    }
  }

  console.log(`\n📊 Summary:`);
  console.log(`   ✅ Generated: ${successCount}`);
  console.log(`   ❌ Errors: ${errorCount}`);
  console.log(`\n📁 Output: ${outputDir}/`);

  if (errorCount > 0) {
    process.exit(1);
  }
}

// Run
generateSkills().catch((error) => {
  console.error('Fatal error:', error);
  process.exit(1);
});
