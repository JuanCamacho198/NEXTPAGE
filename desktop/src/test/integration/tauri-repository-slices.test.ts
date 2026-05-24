import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = resolve(process.cwd(), 'src-tauri', 'src');

describe('repository domain extraction slices', () => {
  it('delegates domain methods from repository facade to domain modules', () => {
    const modRs = readFileSync(resolve(root, 'repository/mod.rs'), 'utf8');
    const expected = [
      'progress::get_progress(self, book_id)',
      'highlights::list_highlights(self, book_id)',
      'bookmarks::list_bookmarks(self, book_id)',
      'collections::list_collections(self)',
      'search::search_book_text(self, payload)'
    ];
    for (const marker of expected) {
      expect(modRs.includes(marker), `missing delegation: ${marker}`).toBe(true);
    }
  });

  it('implements domain modules with concrete functions (not only re-exports)', () => {
    const modules = ['progress','highlights','bookmarks','collections','search'];
    for (const name of modules) {
      const source = readFileSync(resolve(root, `repository/${name}.rs`), 'utf8');
      expect(source.includes('pub fn '), `${name}.rs must define functions`).toBe(true);
    }
  });
});
