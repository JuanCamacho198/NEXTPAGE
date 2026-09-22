/**
 * Overlay-pattern freeze (perf-stability-cleanup P3).
 *
 * Decision: the ten hand-rolled overlay roots below stay hand-rolled.
 * Migrating them to bits-ui is weeks of work and was explicitly deferred;
 * what ends today is the ambiguity, not the migration. New overlay roots
 * must either reuse Modal/Dialog (preferred) or update this allowlist
 * with a recorded reason — never silently add an eleventh `fixed inset-0`.
 */
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative, resolve, sep } from 'node:path';
import { describe, expect, it } from 'vitest';

const SKIPPED_DIRS = new Set(['node_modules', 'dist', 'build', '.svelte-kit']);

/** A hand-rolled overlay root: Tailwind fixed full-viewport backdrop. */
const OVERLAY_ROOT = /fixed\s+inset-0/;

/**
 * Exact known set, relative to src/lib with forward slashes. Sorted.
 * Removing an entry (migration) or adding one (new overlay) fails loudly
 * so the freeze decision is revisited on purpose, never by drift.
 */
const KNOWN_OVERLAY_ROOTS = [
  'features/library/components/CollectionManager.svelte',
  'features/reader/chrome/BookmarkSidebar.svelte',
  'features/reader/chrome/ReaderTextSettings.svelte',
  'features/reader/chrome/ReaderTocPanel.svelte',
  'features/reader/chrome/ReaderWorkspace.svelte',
  'features/reader/highlight/NoteEditorModal.svelte',
  'features/settings/components/SettingsPanel.svelte',
  'shared/ui/feedback/ErrorFallback.svelte',
  'shared/ui/feedback/FeedbackDialog.svelte',
  'shared/ui/layout/Modal.svelte',
];

function resolveLibPath(): string {
  const candidates = [
    resolve(process.cwd(), 'src', 'lib'),
    resolve(process.cwd(), 'desktop', 'src', 'lib'),
  ];
  const found = candidates.find((dir) => {
    try {
      return readdirSync(dir, { withFileTypes: true }).length > 0;
    } catch {
      return false;
    }
  });
  if (!found) throw new Error('src/lib not found from ' + process.cwd());
  return found;
}

function collectSvelteFiles(dir: string): string[] {
  const out: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    if (SKIPPED_DIRS.has(entry.name)) continue;
    const full = join(dir, entry.name);
    if (entry.isDirectory()) out.push(...collectSvelteFiles(full));
    else if (entry.isFile() && entry.name.endsWith('.svelte')) out.push(full);
  }
  return out;
}

function overlayRoots(): string[] {
  const lib = resolveLibPath();
  return collectSvelteFiles(lib)
    .filter((file) => OVERLAY_ROOT.test(readFileSync(file, 'utf8')))
    .map((file) => relative(lib, file).split(sep).join('/'))
    .sort();
}

describe('overlay pattern freeze', () => {
  it('pins the exact set of hand-rolled overlay roots', () => {
    const actual = overlayRoots();
    expect(
      actual,
      'new or removed hand-rolled overlay root — update KNOWN_OVERLAY_ROOTS with a recorded reason',
    ).toEqual(KNOWN_OVERLAY_ROOTS);
  });

  it('keeps FeedbackDialog fully hand-rolled (no bits-ui import)', () => {
    const lib = resolveLibPath();
    const source = readFileSync(join(lib, 'shared/ui/feedback/FeedbackDialog.svelte'), 'utf8');
    expect(source.includes('bits-ui')).toBe(false);
  });
});
