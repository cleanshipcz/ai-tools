import { join } from 'path';
import { mkdir, writeFile, rm } from 'fs/promises';
import { ConfigService } from './config.service.js';
import { LoaderService } from './loader.service.js';
import { Skill } from '../models/types.js';
import chalk from 'chalk';

export class SkillService {
  private config = ConfigService.getInstance();
  private loader = new LoaderService();

  async generateSkills(): Promise<void> {
    console.log(chalk.blue('🛠️  Generating Anthropic-compatible SKILL.md files...\n'));

    const outputDir = join(process.cwd(), 'adapters', 'claude-code', 'skills');

    // Clean and create output directory
    try {
      await rm(outputDir, { recursive: true, force: true });
    } catch (error) {
      // Ignore
    }
    await mkdir(outputDir, { recursive: true });

    const skillsDir = this.config.getPath(this.config.dirs.skills);
    const skillFiles = await this.loader.findYamlFiles(skillsDir);

    if (skillFiles.length === 0) {
      console.error(chalk.red('❌ No skill files found'));
      return;
    }

    console.log(chalk.gray(`Found ${skillFiles.length} skills\n`));

    let successCount = 0;
    let errorCount = 0;

    for (const file of skillFiles) {
      try {
        const skill = await this.loader.loadYaml<Skill>(file);

        if (!skill.id || !skill.description) {
          console.error(chalk.yellow(`⚠️  ${file}: Missing required fields (id or description)`));
          errorCount++;
          continue;
        }

        const markdown = this.generateSkillMarkdown(skill);

        const skillDir = join(outputDir, skill.id);
        await mkdir(skillDir, { recursive: true });

        const skillPath = join(skillDir, 'SKILL.md');
        await writeFile(skillPath, markdown, 'utf-8');

        console.log(chalk.gray(`✅ ${skill.id}/SKILL.md`));
        successCount++;
      } catch (error: any) {
        console.error(chalk.red(`❌ Failed to process ${file}:`), error.message);
        errorCount++;
      }
    }

    console.log(chalk.bold(`\n📊 Summary:`));
    console.log(chalk.green(`   ✅ Generated: ${successCount}`));
    console.log(chalk.red(`   ❌ Errors: ${errorCount}`));
    console.log(chalk.gray(`\n📁 Output: ${outputDir}/\n`));
  }

  private generateSkillMarkdown(skill: Skill): string {
    const lines: string[] = [];

    lines.push('---');
    lines.push(`name: ${skill.id}`);
    lines.push(`description: ${skill.description}`);
    lines.push('---');
    lines.push('');

    const title = skill.id
      .split('-')
      .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
    lines.push(`# ${title}`);
    lines.push('');

    lines.push('## When to use this skill');
    lines.push('');
    // Skill interface in types.ts doesn't have tags? Let's check.
    // It does have tags?: string[];
    if (skill.tags && skill.tags.length > 0) {
      lines.push('Use this skill when:');
      lines.push(`- Working with ${skill.tags.join(', ')}`);
      lines.push(`- User asks to "${skill.id.replace(/-/g, ' ')}"`);
    } else {
      lines.push(`Use this skill when: ${skill.description}`);
    }
    lines.push('');

    // Constraints not in types.ts?
    // types.ts: Skill has id, version, description, command, mcp_tool, timeout_sec, inputs, outputs.
    // gen-skills.ts interface had constraints, retry.
    // I should update types.ts if needed, or use 'any' cast if I want to support extra fields without updating types yet.
    // Or better, update types.ts.
    // For now, I'll check if property exists by casting to any to avoid TS errors if types are missing.
    const s = skill as any;

    if (s.constraints && s.constraints.length > 0) {
      lines.push('## Prerequisites');
      lines.push('');
      for (const constraint of s.constraints) {
        lines.push(`- ${constraint}`);
      }
      lines.push('');
    }

    if (skill.command) {
      lines.push('## How to use');
      lines.push('');

      const args = skill.command.args ? ` ${skill.command.args.join(' ')}` : '';
      lines.push('Run the following command:');
      lines.push('');
      lines.push('```bash');
      lines.push(`${skill.command.program || 'command'}${args}`); // program might be missing in types.ts?
      // types.ts: command?: any;
      // So no type checking there.
      lines.push('```');
      lines.push('');

      if (skill.command.cwd && skill.command.cwd !== '.') {
        lines.push(`Working directory: \`${skill.command.cwd}\``);
        lines.push('');
      }

      if (skill.command.env && Object.keys(skill.command.env).length > 0) {
        lines.push('### Environment variables');
        lines.push('');
        for (const [key, value] of Object.entries(skill.command.env)) {
          lines.push(`- \`${key}\`: ${value}`);
        }
        lines.push('');
      }

      if (skill.timeout_sec) {
        lines.push(`**Timeout:** ${skill.timeout_sec} seconds`);
        lines.push('');
      }

      if (s.retry) {
        lines.push('### Retry configuration');
        lines.push('');
        lines.push(`- Max attempts: ${s.retry.max_attempts}`);
        lines.push(`- Backoff: ${s.retry.backoff_ms}ms`);
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

    if (skill.inputs && skill.inputs.length > 0) {
      lines.push('## Inputs');
      lines.push('');
      for (const input of skill.inputs) {
        const required = input.required ? ' (required)' : ' (optional)'; // input is any in types.ts
        lines.push(`### \`${input.name}\`${required}`);
        lines.push('');
        lines.push(`- Type: \`${input.type}\``);
        if (input.description) {
          lines.push(`- Description: ${input.description}`);
        }
        lines.push('');
      }
    }

    if (skill.outputs) {
      lines.push('## Understanding the output');
      lines.push('');

      if (skill.outputs.exit_code) { // outputs is any in types.ts
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

    lines.push('## Examples');
    lines.push('');
    lines.push('### Basic usage');
    lines.push('');
    lines.push('```');
    lines.push(`User: "Can you ${skill.id.replace(/-/g, ' ')}?"`);
    if (skill.command) {
      const args = skill.command.args ? ` ${skill.command.args.join(' ')}` : '';
      lines.push(`Assistant: [Runs: ${skill.command.program || 'command'}${args}]`);
    }
    lines.push('Assistant: [Interprets output and reports results]');
    lines.push('```');
    lines.push('');

    lines.push('## Troubleshooting');
    lines.push('');
    if (skill.command) {
      lines.push('### Command not found');
      lines.push('');
      lines.push(`Ensure \`${skill.command.program || 'command'}\` is installed and available in the PATH.`);
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

    // Metadata footer - not in types.ts?
    // types.ts: Skill doesn't have metadata?
    // It doesn't. I'll cast to any.
    if (s.metadata) {
      lines.push('---');
      lines.push('');
      if (s.metadata.author) {
        lines.push(`**Author:** ${s.metadata.author}`);
      }
      if (skill.version) {
        lines.push(`**Version:** ${skill.version}`);
      }
      if (s.metadata.created) {
        lines.push(`**Created:** ${s.metadata.created}`);
      }
      if (s.metadata.updated) {
        lines.push(`**Updated:** ${s.metadata.updated}`);
      }
    }

    return lines.join('\n');
  }
}
