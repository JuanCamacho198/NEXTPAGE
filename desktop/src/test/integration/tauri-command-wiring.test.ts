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
    const symbols = [
      'listBooks',
      'getSettings',
      'upsertSettings',
      'saveProgress',
      'listCollections',
      'getFileBytes',
      'listTagsForHighlight',
    ];
    for (const symbol of symbols) {
      expect(mainRs.includes(`commands::${symbol}`)).toBe(true);
      expect(
        commandMod.includes(`pub fn ${symbol}`) || commandMod.includes(`pub async fn ${symbol}`),
      ).toBe(true);
    }
  });

  it('registers addCoalescedSyncOutboxItem end-to-end (WU4 IPC surface)', () => {
    const mainRs = readFileSync(resolve(root, 'main.rs'), 'utf8');
    const commandMod = readFileSync(resolve(root, 'commands/mod.rs'), 'utf8');
    const outboxRs = readFileSync(resolve(root, 'commands/outbox.rs'), 'utf8');
    const symbol = 'addCoalescedSyncOutboxItem';
    // 1. Registered in the tauri invoke_handler.
    expect(mainRs.includes(`commands::${symbol}`)).toBe(true);
    // 2. Public command fn exists in the commands module.
    expect(commandMod.includes(`pub fn ${symbol}`)).toBe(true);
    // 3. Re-exported through the outbox feature module.
    expect(outboxRs.includes(symbol)).toBe(true);
  });
});
