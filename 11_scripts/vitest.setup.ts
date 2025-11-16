/**
 * Global setup for Vitest
 */

import { mkdirSync, rmSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';
import { existsSync } from 'fs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const ROOT_DIR = join(__dirname, '..');
const TEST_OUTPUT_DIR = join(ROOT_DIR, '.output-test');

export function setup() {
  // Ensure test output directory exists (synchronous)
  mkdirSync(TEST_OUTPUT_DIR, { recursive: true });
  console.log('✓ Test environment initialized');
}

export function teardown() {
  // Clean up test output directory (synchronous)
  if (existsSync(TEST_OUTPUT_DIR)) {
    rmSync(TEST_OUTPUT_DIR, { recursive: true, force: true });
  }
  console.log('✓ Test environment cleaned up');
}
