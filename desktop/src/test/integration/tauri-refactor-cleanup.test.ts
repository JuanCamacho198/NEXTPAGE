import { describe, expect, it } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.cwd(), 'src-tauri', 'src');
const docsRoot = resolve(process.cwd(), 'src-tauri', 'docs');

describe('tauri refactor cleanup and rollback readiness', () => {
  it('documents transitional facade cleanup and final removal gate', () => {
    const commandsMod = readFileSync(resolve(root, 'commands/mod.rs'), 'utf8');
    const repoMod = readFileSync(resolve(root, 'repository/mod.rs'), 'utf8');

    // commands/mod.rs was cleaned up — only camelCase commands remain
    expect(commandsMod.includes('only camelCase commands remain')).toBe(true);
    expect(repoMod.includes('TRANSITION FACADE')).toBe(true);
    expect(repoMod.includes('REMOVE ONLY AFTER VERIFY')).toBe(true);
  });

  it('provides rollback checklist by slice for PR handoff', () => {
    const checklistPath = resolve(docsRoot, 'refactor-tauri-backend-rollback-checklist.md');
    expect(existsSync(checklistPath)).toBe(true);

    const checklist = readFileSync(checklistPath, 'utf8');
    for (const marker of [
      'settings/library',
      'progress/highlights',
      'bookmarks/collections/search/files',
      'verify gate',
    ]) {
      expect(checklist.includes(marker), `missing ${marker}`).toBe(true);
    }
  });
});
