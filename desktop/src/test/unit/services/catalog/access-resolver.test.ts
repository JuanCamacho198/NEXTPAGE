import { describe, expect, it } from 'vitest';
import {
  BUILTIN_GUTENDEX,
  BUILTIN_OPENLIBRARY,
} from '$lib/shared/services/catalog/CatalogProvider';
import type { CatalogBook } from '$lib/shared/services/catalog/CatalogProvider';
import { isHttpsUrl, resolveAccess } from '$lib/shared/services/catalog/accessResolver';

const PD_DOWNLOAD_URL = 'https://www.gutenberg.org/cache/epub/1342/pg1342.txt';
const INSECURE_DOWNLOAD_URL = 'http://www.gutenberg.org/cache/epub/1342/pg1342.txt';

function book(overrides: Partial<CatalogBook> = {}): CatalogBook {
  return {
    id: 'gutendex:1342',
    provider: BUILTIN_GUTENDEX,
    title: 'Pride and Prejudice',
    authors: ['Jane Austen'],
    coverUrl: null,
    languages: ['en'],
    subjects: [],
    downloadUrl: PD_DOWNLOAD_URL,
    isPublicDomain: true,
    ...overrides,
  };
}

describe('isHttpsUrl', () => {
  it('accepts only non-blank https URLs', () => {
    expect(isHttpsUrl('https://example.com')).toBe(true);
    expect(isHttpsUrl('http://example.com')).toBe(false);
    expect(isHttpsUrl('  ')).toBe(false);
    expect(isHttpsUrl(null)).toBe(false);
    expect(isHttpsUrl(undefined)).toBe(false);
  });
});

describe('resolveAccess (PD gate)', () => {
  it('resolves the in-app download only for a PD book with an https URL', () => {
    const access = resolveAccess(book());
    expect(access.canDownloadInApp).toBe(true);
    expect(access.downloadUrl).toBe(PD_DOWNLOAD_URL);
    const download = access.options.find((option) => option.opensInApp);
    expect(download).toEqual({
      group: 'FREE',
      titleKey: 'discover.accessDownload',
      url: PD_DOWNLOAD_URL,
      opensInApp: true,
    });
  });

  it('resolves external-only for in-copyright and unknown-PD books', () => {
    for (const isPublicDomain of [false, null]) {
      const access = resolveAccess(book({ isPublicDomain }));
      expect(access.canDownloadInApp).toBe(false);
      expect(access.downloadUrl).toBeNull();
      expect(access.options.some((option) => option.opensInApp)).toBe(false);
    }
  });

  it('never resolves an in-app path from a non-https download URL', () => {
    const access = resolveAccess(book({ downloadUrl: INSECURE_DOWNLOAD_URL }));
    expect(access.canDownloadInApp).toBe(false);
    expect(access.downloadUrl).toBeNull();
    access.options.forEach((option) => expect(option.url.startsWith('https://')).toBe(true));
  });

  it('fails closed when the book has no download URL at all', () => {
    expect(resolveAccess(book({ downloadUrl: null })).canDownloadInApp).toBe(false);
    expect(resolveAccess(book({ downloadUrl: '   ' })).canDownloadInApp).toBe(false);
  });
});

describe('resolveAccess (groups and links)', () => {
  it('covers FREE, BUY, and SUBSCRIBE with https-only links', () => {
    const access = resolveAccess(
      book({ openLibraryWorkId: '/works/OL66554W', isbn13: '9780141439518' }),
    );
    expect(new Set(access.options.map((option) => option.group))).toEqual(
      new Set(['FREE', 'BUY', 'SUBSCRIBE']),
    );
    expect(access.bookId).toBe('gutendex:1342');
    access.options.forEach((option) => {
      expect(option.url.startsWith('https://')).toBe(true);
      expect(option.url.toLowerCase()).not.toContain('borrow');
      expect(option.url.toLowerCase()).not.toContain('lending');
    });
  });

  it('builds canonical identity links for work key, IA id, volume id, and Gutenberg id', () => {
    const access = resolveAccess(
      book({
        downloadUrl: null,
        openLibraryWorkId: 'works/OL66554W',
        internetArchiveId: 'prideandprejudice0000aust',
        googleBooksId: 'abc123',
      }),
    );
    const freeUrls = access.options
      .filter((option) => option.group === 'FREE')
      .map((option) => option.url);
    expect(freeUrls).toContain('https://openlibrary.org/works/OL66554W');
    expect(freeUrls).toContain('https://archive.org/details/prideandprejudice0000aust');
    expect(freeUrls).toContain('https://books.google.com/books?id=abc123');
    expect(freeUrls).toContain('https://www.gutenberg.org/ebooks/1342');
  });

  it('links the ISBN buy/subscribe queries when the ISBN pair is present', () => {
    const byIsbn13 = resolveAccess(book({ isbn13: '9780141439518' }));
    const buy = byIsbn13.options.find((option) => option.group === 'BUY');
    expect(buy?.url.startsWith('https://www.google.com/search?q=')).toBe(true);
    expect(buy?.url).toContain(encodeURIComponent('isbn 9780141439518 buy book'));
    expect(byIsbn13.options.some((option) => option.titleKey === 'discover.accessBuy')).toBe(true);

    // isbn10 is the fallback when isbn13 is absent.
    const byIsbn10 = resolveAccess(book({ isbn10: '0141439513' }));
    expect(byIsbn10.options.find((option) => option.group === 'BUY')?.url).toContain(
      encodeURIComponent('isbn 0141439513'),
    );
  });

  it('falls back to a generic web search when the book has no identity', () => {
    const access = resolveAccess(
      book({
        id: 'openlibrary:/works/OL00000W',
        provider: BUILTIN_OPENLIBRARY,
        downloadUrl: null,
        isPublicDomain: null,
      }),
    );
    const free = access.options.filter((option) => option.group === 'FREE');
    expect(free.map((option) => option.titleKey)).toEqual(['discover.accessWebSearch']);
    expect(free[0]?.url).toBe(
      `https://www.google.com/search?q=${encodeURIComponent('Pride and Prejudice Jane Austen')}`,
    );
    expect(new Set(access.options.map((option) => option.group))).toEqual(
      new Set(['FREE', 'BUY', 'SUBSCRIBE']),
    );
  });

  it('does not add the generic free search when an identity link already exists', () => {
    const access = resolveAccess(book({ downloadUrl: null, isbn13: '9780141439518' }));
    const freeTitles = access.options
      .filter((option) => option.group === 'FREE')
      .map((option) => option.titleKey);
    expect(freeTitles).toEqual(['discover.accessGutenberg']);
  });

  it('caps the author query at three names and never throws on malformed ids', () => {
    const access = resolveAccess(
      book({
        id: 'gutendex:abc',
        authors: ['A', 'B', 'C', 'D'],
        downloadUrl: null,
        isPublicDomain: null,
      }),
    );
    expect(access.canDownloadInApp).toBe(false);
    const buy = access.options.find((option) => option.group === 'BUY');
    expect(buy?.url).toContain(encodeURIComponent('Pride and Prejudice A, B, C buy book'));
    // Malformed Gutenberg ids yield no ebook link.
    expect(
      access.options.some((option) => option.url.startsWith('https://www.gutenberg.org/ebooks/')),
    ).toBe(false);
  });
});
