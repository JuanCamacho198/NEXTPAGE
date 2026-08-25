import { describe, expect, it, vi } from 'vitest';
import { createSpineResolver } from '$lib/features/reader/chrome/useSpineResolver.svelte';

describe('useSpineResolver', () => {
  it('resolves exact href', () => {
    const resolver = createSpineResolver({ parseEpub: vi.fn() });
    const spine = ['OEBPS/Text/chapter1.xhtml', 'OEBPS/Text/chapter2.xhtml'];
    expect(resolver.getSpineIndexForHref('OEBPS/Text/chapter1.xhtml', spine)).toBe(0);
    expect(resolver.getSpineIndexForHref('OEBPS/Text/chapter2.xhtml', spine)).toBe(1);
  });

  it('resolves via filename suffix fallback (OEBPS/Text prefix variance)', () => {
    const resolver = createSpineResolver({ parseEpub: vi.fn() });
    const spine = ['OEBPS/Text/HM-colombia-1.html', 'OEBPS/Text/HM-colombia-2.html'];
    expect(resolver.getSpineIndexForHref('Text/HM-colombia-1.html', spine)).toBe(0);
    expect(resolver.getSpineIndexForHref('HM-colombia-2.html', spine)).toBe(1);
  });

  it('resolves via endsWith fallback (readium: prefix and fragment stripped)', () => {
    const resolver = createSpineResolver({ parseEpub: vi.fn() });
    const spine = ['OEBPS/Text/cap1.xhtml', 'OEBPS/Text/cap2.xhtml'];
    expect(resolver.getSpineIndexForHref('readium:OEBPS/Text/cap1.xhtml#frag', spine)).toBe(0);
    expect(resolver.getSpineIndexForHref('OEBPS/Text/cap2.xhtml#_idParaDest-5', spine)).toBe(1);
    // also handles backslash normalization
    expect(resolver.getSpineIndexForHref('OEBPS\\Text\\cap1.xhtml', spine)).toBe(0);
  });

  it('returns null for empty or not found', () => {
    const resolver = createSpineResolver({ parseEpub: vi.fn() });
    const spine = ['a.xhtml', 'b.xhtml'];
    expect(resolver.getSpineIndexForHref('', spine)).toBeNull();
    expect(resolver.getSpineIndexForHref('   ', spine)).toBeNull();
    expect(resolver.getSpineIndexForHref('notfound.xhtml', spine)).toBeNull();
  });

  it('ensureSpineHrefs caches and calls parseEpub once per book', async () => {
    const parseMock = vi.fn().mockResolvedValue({ spineHrefs: ['a.xhtml', 'b.xhtml'] });
    const resolver = createSpineResolver({ parseEpub: parseMock });
    await resolver.ensureSpineHrefs('book-1', 'C:/book.epub');
    expect(parseMock).toHaveBeenCalledTimes(1);
    expect(resolver.epubSpineHrefs).toEqual(['a.xhtml', 'b.xhtml']);
    expect(resolver.epubSpineLoadedFor).toBe('book-1');
    // second call same book should be cached
    await resolver.ensureSpineHrefs('book-1', 'C:/book.epub');
    expect(parseMock).toHaveBeenCalledTimes(1);
    // different book triggers new fetch
    await resolver.ensureSpineHrefs('book-2', 'C:/other.epub');
    expect(parseMock).toHaveBeenCalledTimes(2);
  });
});
