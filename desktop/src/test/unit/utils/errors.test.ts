/**
 * Unit tests for `desktop/src/lib/shared/utils/errors.ts`.
 *
 * Verifies the `reader-error-context` capability scenarios:
 *  - "withContext merges into existing context"
 *  - "withContext with empty object is a no-op"
 *  - "withContext overwrites conflicting keys (last write wins)" — implied by
 *    the spec semantics; we lock the direction with a dedicated test.
 *  - ReaderError defaults: source='reader', category='runtime'.
 *  - Immutability: receiver is NEVER mutated (callers must use the returned
 *    error or the new context will be lost).
 */
import { describe, expect, it } from 'vitest';
import { AppError, ReaderError } from '$lib/shared/utils/errors';

describe('ReaderError — constructor defaults', () => {
  it('defaults source to "reader" and category to "runtime"', () => {
    const err = new ReaderError('boom');
    expect(err.source).toBe('reader');
    expect(err.category).toBe('runtime');
    expect(err.code).toBe('READER_ERROR');
    expect(err.context).toEqual({});
  });

  it('is an AppError so handleError() keeps the source="reader" tag', () => {
    const err = new ReaderError('boom');
    expect(err).toBeInstanceOf(AppError);
    expect(err).toBeInstanceOf(ReaderError);
  });
});

describe('ReaderError.withContext', () => {
  it('merges into existing context without losing prior keys', () => {
    const base = new ReaderError('boom', 'READER_FAIL', { format: 'epub' });
    const merged = base.withContext({ bookId: 'abc', cfi: 'epubcfi(/6/2)' });
    expect(merged.context).toEqual({
      format: 'epub',
      bookId: 'abc',
      cfi: 'epubcfi(/6/2)',
    });
  });

  it('is immutable: the original context is unchanged after withContext', () => {
    const base = new ReaderError('boom', 'READER_FAIL', { format: 'epub' });
    const before = { ...base.context };
    base.withContext({ bookId: 'abc' });
    expect(base.context).toEqual(before);
  });

  it('returns a fresh instance, NOT the same reference', () => {
    const base = new ReaderError('boom');
    const merged = base.withContext({ bookId: 'abc' });
    expect(merged).not.toBe(base);
    expect(merged.context).not.toBe(base.context);
  });

  it('with an empty object returns equal context (no-op semantics)', () => {
    const base = new ReaderError('boom', 'READER_FAIL', { format: 'epub', bookId: 'abc' });
    const merged = base.withContext({});
    expect(merged.context).toEqual(base.context);
    // Still a fresh instance.
    expect(merged).not.toBe(base);
    expect(merged.context).not.toBe(base.context);
  });

  it('overwrites conflicting keys (last write wins — extra over context)', () => {
    const base = new ReaderError('boom', 'READER_FAIL', { format: 'epub', pageNumber: 1 });
    const merged = base.withContext({ pageNumber: 5, action: 'index_text' });
    expect(merged.context['pageNumber']).toBe(5);
    expect(merged.context['action']).toBe('index_text');
    // Non-conflicting key preserved.
    expect(merged.context['format']).toBe('epub');
  });
});