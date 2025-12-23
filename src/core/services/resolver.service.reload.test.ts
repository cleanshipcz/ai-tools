import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { ResolverService } from './resolver.service.js';
import { ConfigService } from './config.service.js';
import { join } from 'path';
import { mkdtemp } from 'fs/promises';
import { tmpdir } from 'os';
import { mkdir, rm, writeFile } from 'fs/promises';

describe('ResolverService ruleset reloading', () => {
  let tempDir: string;
  let rulesetsDir: string;
  let originalGetPath: ConfigService['getPath'];

  beforeEach(async () => {
    tempDir = await mkdtemp(join(tmpdir(), 'resolver-ruleset-'));
    rulesetsDir = join(tempDir, 'rulesets');
    await mkdir(rulesetsDir, { recursive: true });

    const config = ConfigService.getInstance();
    originalGetPath = config.getPath.bind(config);

    vi.spyOn(config, 'getPath').mockImplementation((...parts: string[]) => {
      if (parts[0] === config.dirs.rulesets) {
        return join(rulesetsDir, ...parts.slice(1));
      }
      return originalGetPath(...parts);
    });
  });

  afterEach(async () => {
    vi.restoreAllMocks();
    await rm(tempDir, { recursive: true, force: true });
  });

  it('picks up changes to a ruleset without restarting the process', async () => {
    const baseRule = 'Original base rule';
    const updatedRule = 'Updated base rule';
    const basePath = join(rulesetsDir, 'base.yml');

    await writeFile(
      basePath,
      [
        'id: base',
        'version: 1.0.0',
        'rules:',
        `  - "${baseRule}"`,
      ].join('\n'),
      'utf-8'
    );

    const resolver = new ResolverService();

    const initialRules = await resolver.resolveRulesets(['base']);
    expect(initialRules).toEqual([baseRule]);

    await writeFile(
      basePath,
      [
        'id: base',
        'version: 1.0.0',
        'rules:',
        `  - "${baseRule}"`,
        `  - "${updatedRule}"`,
      ].join('\n'),
      'utf-8'
    );

    const refreshedRules = await resolver.resolveRulesets(['base']);
    expect(refreshedRules).toEqual([baseRule, updatedRule]);
  });
});
