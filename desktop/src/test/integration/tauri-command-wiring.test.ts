import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.cwd(), 'src-tauri', 'src');

describe('tauri command wiring compatibility', () => {
  it('routes command surface through feature module re-exports', () => {
    const modRs = readFileSync(resolve(root, 'commands/mod.rs'), 'utf8');
    for (const line of [
      'pub use settings::*;',
      'pub use library::*;',
      'pub use progress::*;',
      'pub use highlights::*;',
      'pub use bookmarks::*;',
      'pub use collections::*;',
      'pub use search::*;',
      'pub use files::*;',
    ]) {
      expect(modRs.includes(line), `missing ${line}`).toBe(true);
    }
  });

  it('keeps invoke handler public command symbols available from commands module', () => {
    const mainRs = readFileSync(resolve(root, 'main.rs'), 'utf8');
    const commandMod = readFileSync(resolve(root, 'commands/mod.rs'), 'utf8');
    // Commands live in feature modules re-exported through mod.rs
    // (backend split; mod.rs itself only carries `pub use` lines).
    const home: Record<string, string> = {
      listBooks: 'library.rs',
      getSettings: 'settings.rs',
      upsertSettings: 'settings.rs',
      saveProgress: 'progress.rs',
      listCollections: 'collections.rs',
      getFileBytes: 'files.rs',
      listTagsForHighlight: 'highlights.rs',
    };
    for (const [symbol, file] of Object.entries(home)) {
      expect(mainRs.includes(`commands::${symbol}`)).toBe(true);
      const featureRs = readFileSync(resolve(root, 'commands', file), 'utf8');
      expect(
        featureRs.includes(`pub fn ${symbol}`) || featureRs.includes(`pub async fn ${symbol}`),
      ).toBe(true);
      expect(commandMod.includes(`pub use ${file.replace(/\.rs$/, '')}::*;`)).toBe(true);
    }
  });

  it('registers addCoalescedSyncOutboxItem end-to-end (WU4 IPC surface)', () => {
    const mainRs = readFileSync(resolve(root, 'main.rs'), 'utf8');
    const commandMod = readFileSync(resolve(root, 'commands/mod.rs'), 'utf8');
    const outboxRs = readFileSync(resolve(root, 'commands/outbox.rs'), 'utf8');
    const symbol = 'addCoalescedSyncOutboxItem';
    // 1. Registered in the tauri invoke_handler.
    expect(mainRs.includes(`commands::${symbol}`)).toBe(true);
    // 2. Public command fn exists in its feature module (re-exported via mod.rs).
    expect(
      outboxRs.includes(`pub fn ${symbol}`) || outboxRs.includes(`pub async fn ${symbol}`),
    ).toBe(true);
    expect(commandMod.includes('pub use outbox::*;')).toBe(true);
    // 3. Re-exported through the outbox feature module.
    expect(outboxRs.includes(symbol)).toBe(true);
  });
});
