/**
 * TDD tests for schema cleanup - removing deprecated fields
 *
 * These tests define what should NOT be in schemas after cleanup:
 * - preferred_agents should not exist in project schema
 * - entry_points should not exist in feature schema
 * - snippets should not exist in feature schema
 *
 * RED phase: These tests will FAIL until we clean up the schemas
 */

import { describe, it, expect } from 'vitest';
import { readFileSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const REPO_ROOT = join(__dirname, '..');

describe('Schema Cleanup - TDD Red Phase', () => {
  describe('project.schema.json', () => {
    it('should NOT contain preferred_agents property', () => {
      const schemaPath = join(REPO_ROOT, '10_schemas', 'project.schema.json');
      const schemaContent = readFileSync(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      // Navigate to ai_tools section (it's in properties, not definitions)
      const aiToolsProps = schema.properties?.ai_tools?.properties;
      expect(aiToolsProps).toBeDefined();

      // This should FAIL initially - preferred_agents should not exist
      expect(aiToolsProps).not.toHaveProperty('preferred_agents');
    });

    it('should still contain other ai_tools properties', () => {
      const schemaPath = join(REPO_ROOT, '10_schemas', 'project.schema.json');
      const schemaContent = readFileSync(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      const aiToolsProps = schema.properties?.ai_tools?.properties;

      // These should still exist
      expect(aiToolsProps).toHaveProperty('model');
      expect(aiToolsProps).toHaveProperty('preferred_rulepacks');
      expect(aiToolsProps).toHaveProperty('whitelist_agents');
    });
  });

  describe('feature.schema.json', () => {
    it('should NOT contain entry_points in files section', () => {
      const schemaPath = join(REPO_ROOT, '10_schemas', 'feature.schema.json');
      const schemaContent = readFileSync(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      const filesProps = schema.properties?.files?.properties;
      expect(filesProps).toBeDefined();

      // This should FAIL initially - entry_points should not exist
      expect(filesProps).not.toHaveProperty('entry_points');
    });

    it('should NOT contain key_files in files section', () => {
      const schemaPath = join(REPO_ROOT, '10_schemas', 'feature.schema.json');
      const schemaContent = readFileSync(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      const filesProps = schema.properties?.files?.properties;
      expect(filesProps).toBeDefined();

      // This should FAIL initially - key_files should not exist
      expect(filesProps).not.toHaveProperty('key_files');
    });

    it('should NOT contain snippets property', () => {
      const schemaPath = join(REPO_ROOT, '10_schemas', 'feature.schema.json');
      const schemaContent = readFileSync(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      // This should FAIL initially - snippets should not exist
      expect(schema.properties).not.toHaveProperty('snippets');
    });

    it('should still contain patterns in files section', () => {
      const schemaPath = join(REPO_ROOT, '10_schemas', 'feature.schema.json');
      const schemaContent = readFileSync(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      const filesProps = schema.properties?.files?.properties;

      // This should still exist
      expect(filesProps).toHaveProperty('patterns');
    });

    it('should still contain other feature properties', () => {
      const schemaPath = join(REPO_ROOT, '10_schemas', 'feature.schema.json');
      const schemaContent = readFileSync(schemaPath, 'utf-8');
      const schema = JSON.parse(schemaContent);

      // These should still exist
      expect(schema.properties).toHaveProperty('id');
      expect(schema.properties).toHaveProperty('version');
      expect(schema.properties).toHaveProperty('name');
      expect(schema.properties).toHaveProperty('description');
      expect(schema.properties).toHaveProperty('context');
      expect(schema.properties).toHaveProperty('conventions');
    });
  });
});
