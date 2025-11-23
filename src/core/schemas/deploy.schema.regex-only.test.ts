import { readFile } from 'fs/promises';
import { join } from 'path';
import Ajv from 'ajv';
import addFormats from 'ajv-formats';

describe('deploy schema rejects legacy filters', () => {
  it('rejects legacy whitelist/blacklist filter lists', async () => {
    const schemaPath = join(process.cwd(), '10_schemas', 'deploy.schema.json');
    const schema = JSON.parse(await readFile(schemaPath, 'utf-8'));
    const ajv = new Ajv({ allErrors: true, strict: false });
    addFormats(ajv);
    const validate = ajv.compile(schema);

    const config = {
      target: './out',
      tools: ['github-copilot'],
      mode: 'local',
      ai_tools: {
        whitelist_agents: ['legacy-agent'],
        blacklist_prompts: ['legacy-prompt'],
      },
    };

    const isValid = validate(config);

    expect(isValid).toBe(false);
    const errorJson = JSON.stringify(validate.errors || []);
    expect(errorJson).toContain('whitelist_agents');
    expect(errorJson).toContain('blacklist_prompts');
  });
});
