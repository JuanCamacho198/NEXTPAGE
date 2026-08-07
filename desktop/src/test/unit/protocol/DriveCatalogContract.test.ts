import { describe, expect, it } from 'vitest';
import { DRIVE_BOOKS_PATH, DRIVE_SCOPE, canonicalBookName, canonicalBookPath, mergeCatalogVersion, reconcileLegacyReference, coverError, type Lifecycle, type SyncErrorCode } from '$lib/shared/protocol/DriveCatalogContract';
const files = [{ fileId: 'legacy-id', name: 'old-name.epub', download: async () => new TextEncoder().encode('legacy') }, { fileId: 'canonical-id', name: 'book-1.epub', download: async () => new TextEncoder().encode('book') }];
describe('Drive catalog contract', () => {
  it('builds the canonical folder/file protocol', () => { expect(DRIVE_SCOPE).toContain('/drive.file'); expect(DRIVE_BOOKS_PATH).toBe('NextPage/Books'); expect(canonicalBookName('book-1', '.EPUB')).toBe('book-1.epub'); expect(canonicalBookPath('book-1', 'epub')).toBe('NextPage/Books/book-1.epub'); });
  it('reconciles by id, name, then independently verified hash', async () => { const r = await reconcileLegacyReference('legacy-id', files, 'book-1', 'epub', 'sha256:c49fea7425fa7f8699897a97c159c6690267d9003bb78c53fafa8fc15c325d84'); expect(r?.fileId).toBe('legacy-id'); expect(r?.fileName).toBe('book-1.epub'); expect(r?.canonicalPath).toBe('NextPage/Books/book-1.epub'); expect((await reconcileLegacyReference('missing', files, 'book-1', 'epub', null))?.fileId).toBe('canonical-id'); expect((await reconcileLegacyReference(null, files, 'book-1', 'epub', 'sha256:92719fe0cf8cd51592af31ee8a5736d79f7273777fa3f7b70bfe993a4cd32180'))?.fileId).toBe('canonical-id'); expect(await reconcileLegacyReference('canonical-id', files, 'book-1', 'epub', 'sha256:wrong')).toBeNull(); expect(await reconcileLegacyReference('legacy-id', [{ ...files[0], download: async () => { throw new Error('403'); } }], 'book-1', 'epub', 'sha256:c49fea7425fa7f8699897a97c159c6690267d9003bb78c53fafa8fc15c325d84')).toBeNull(); });
  it('ignores stale events and accepts equal/newer versions', () => { const current = { catalogVersion: 4, lifecycle: 'available' as Lifecycle }; expect(mergeCatalogVersion(current, { catalogVersion: 3, lifecycle: 'deleted' })).toBe(current); expect(mergeCatalogVersion(current, { catalogVersion: 4, lifecycle: 'deleted' }).lifecycle).toBe('deleted'); });
  it('exports COVER_FAILED as a stable SyncErrorCode', () => {
    // REQ-07: upload/invalid/missing cover failures must never block book import.
    const codes: SyncErrorCode[] = ['AUTH_REQUIRED', 'AUTH_EXPIRED', 'PERMISSION_DENIED', 'REMOTE_NOT_FOUND', 'HASH_MISMATCH', 'CONFLICT', 'UNAVAILABLE', 'COVER_FAILED'];
    expect(codes).toContain('COVER_FAILED');
    const err = coverError('corr-1', 'book-9');
    expect(err.code).toBe('COVER_FAILED');
    expect(err.retryable).toBe(true);
    expect(err.correlationId).toBe('corr-1');
    expect(err.bookId).toBe('book-9');
    const errNoBook = coverError('corr-2');
    expect(errNoBook.code).toBe('COVER_FAILED');
    expect(errNoBook.bookId).toBeUndefined();
  });
});
