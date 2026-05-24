import { describe, expect, it } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.cwd(), 'src-tauri', 'src');

describe('tauri backend refactor structure', () => {
  it('creates modular command/repository/model structure', () => {
    const required = [
      'commands/mod.rs',
      'commands/settings.rs',
      'commands/library.rs',
      'commands/progress.rs',
      'commands/highlights.rs',
      'commands/bookmarks.rs',
      'commands/collections.rs',
      'commands/search.rs',
      'commands/files.rs',
      'repository/mod.rs',
      'repository/settings.rs',
      'repository/library.rs',
      'repository/progress.rs',
      'repository/highlights.rs',
      'repository/bookmarks.rs',
      'repository/collections.rs',
      'repository/search.rs',
      'repository/files.rs',
      'models/mod.rs',
      'models/dto.rs',
      'models/domain.rs',
      'models/mapper.rs'
    ];

    for (const rel of required) {
      expect(existsSync(resolve(root, rel)), `missing ${rel}`).toBe(true);
    }
  });

  it('keeps invoke_handler commands registered and dto serde boundary', () => {
    const mainRs = readFileSync(resolve(root, 'main.rs'), 'utf8');
    for (const symbol of ['list_books', 'listBooks', 'save_progress', 'saveProgress', 'get_file_bytes', 'getFileBytes']) {
      expect(mainRs.includes(symbol)).toBe(true);
    }

    const dtoRs = readFileSync(resolve(root, 'models/dto.rs'), 'utf8');
    expect(dtoRs.includes('rename_all = "camelCase"')).toBe(true);
  });
});
