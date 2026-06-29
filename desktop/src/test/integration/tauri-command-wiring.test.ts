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
});
