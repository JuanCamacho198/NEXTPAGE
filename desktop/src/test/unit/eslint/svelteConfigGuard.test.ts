/**
 * Compiler-config guard for the runes guardrail.
 *
 * `desktop/svelte.config.js` must stay free of any global compiler option.
 * The Wave-1 global `runes: true` force was removed because it rejected every
 * published `lucide-svelte` component (`$$props` / `<slot>` output), and the
 * Wave-2 guardrail lives in lint (`local-rules/svelte-runes-only`) instead.
 *
 * This test reads the real config and refuses any change to `compilerOptions`.
 * The synthetic cases below prove the check is able to fail.
 */
import { existsSync, readFileSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function resolveConfigPath(): string {
  const candidates = [
    resolve(process.cwd(), 'svelte.config.js'),
    resolve(process.cwd(), 'desktop', 'svelte.config.js'),
  ];
  const found = candidates.find((candidate) => existsSync(candidate));

  if (!found) {
    throw new Error(`svelte.config.js not found. Looked in: ${candidates.join(', ')}`);
  }

  return found;
}

const CONFIG_PATH = resolveConfigPath();

function stripComments(source: string): string {
  return source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/^\s*\/\/.*$/gm, '');
}

function findCompilerForces(source: string): string[] {
  const stripped = stripComments(source);
  const forces: string[] = [];

  if (/\brunes\b/.test(stripped)) forces.push('runes');

  const compilerOptions = stripped.match(/compilerOptions\s*:\s*\{([^}]*)\}/);
  if (!compilerOptions) {
    forces.push('compilerOptions-missing');
  } else if (compilerOptions[1].trim() !== '') {
    forces.push(`compilerOptions:${compilerOptions[1].trim()}`);
  }

  return forces;
}

describe('svelte.config.js compiler-option guard', () => {
  it('keeps the real config free of compiler options and runes forces', () => {
    const source = readFileSync(CONFIG_PATH, 'utf8');

    expect(findCompilerForces(source)).toEqual([]);
  });

  it('detects a global runes force (non-vacuity)', () => {
    const source = 'export default { compilerOptions: { runes: true } };';

    expect(findCompilerForces(source)).toEqual(['runes', 'compilerOptions:runes: true']);
  });

  it('detects any other compiler option (non-vacuity)', () => {
    const source = 'export default { compilerOptions: { dev: true } };';

    expect(findCompilerForces(source)).toEqual(['compilerOptions:dev: true']);
  });

  it('detects a missing compilerOptions object (non-vacuity)', () => {
    const source = 'export default {};';

    expect(findCompilerForces(source)).toEqual(['compilerOptions-missing']);
  });

  it('does not treat a comment mentioning runes as a force', () => {
    const source = [
      '// NOTE: the global `runes: true` force was removed',
      '/* runes: true */',
      'export default {',
      '  compilerOptions: {},',
      '};',
    ].join('\n');

    expect(findCompilerForces(source)).toEqual([]);
  });
});
