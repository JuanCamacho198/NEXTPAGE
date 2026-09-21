import { beforeEach, describe, expect, it, vi } from 'vitest';
import { invoke } from '@tauri-apps/api/core';
import type { LibraryPort } from '$lib/shared/ports/LibraryPort';
import { i18n } from '$lib/shared/i18n';
import { importBook } from '$lib/shared/services/BookImportService';
import { BulkImportService } from '$lib/shared/services/BulkImportService';
import { validateEpubImportMetadataDto } from '$lib/shared/services/epubImportMetadata';
import {
  ValidationError,
  validateBookImportInput,
  validateBulkImportBatch,
  validateEpubMetadata,
} from '$lib/shared/validation/importSchemas';

const invokeMock = vi.mocked(invoke);

const validInput = {
  sourcePath: '/books/clean-code.epub',
  title: 'Clean Code',
  author: 'Robert C. Martin',
  format: 'epub',
};

beforeEach(() => {
  invokeMock.mockReset();
});

describe('validateBookImportInput', () => {
  it('passes valid input', () => {
    const result = validateBookImportInput(validInput);
    expect(result.ok).toBe(true);
  });

  it('rejects input missing the required format enum', () => {
    const result = validateBookImportInput({ ...validInput, format: 'docx' });
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.key).toBe('import.validation.invalidInput');
      expect(result.fallback.length).toBeGreaterThan(0);
    }
  });

  it('rejects an empty source path', () => {
    expect(validateBookImportInput({ ...validInput, sourcePath: '   ' }).ok).toBe(false);
  });
});

describe('importBook validation gate', () => {
  it('blocks invalid input before Tauri invoke with an i18n-keyed error', async () => {
    await expect(importBook({ ...validInput, format: 'docx' })).rejects.toBeInstanceOf(
      ValidationError,
    );
    expect(invokeMock).not.toHaveBeenCalled();
  });

  it('carries the i18n key and a fallback message on the rejection', async () => {
    const failure = await importBook({ ...validInput, sourcePath: '' }).catch((error) => error);
    expect(failure).toBeInstanceOf(Error);
    expect(typeof failure.message).toBe('string');
    expect(failure.message.length).toBeGreaterThan(0);
  });

  it('reaches Tauri invoke for valid input', async () => {
    invokeMock.mockImplementation((command) => {
      if (command === 'importBook') {
        return Promise.resolve({
          id: 'book-1',
          title: 'Clean Code',
          author: 'Robert C. Martin',
          filePath: '/books/clean-code.epub',
          format: 'epub',
          syncStatus: 'synced',
          currentPage: 0,
          totalPages: 10,
          createdAt: '2026-01-01',
          updatedAt: '2026-01-01',
        });
      }
      return Promise.reject(new Error(`unexpected invoke: ${command}`));
    });

    const book = await importBook(validInput);
    expect(book.id).toBe('book-1');
    expect(invokeMock).toHaveBeenCalledWith('importBook', expect.anything());
  });
});

describe('validateBulkImportBatch', () => {
  it('reports partial success for a mixed batch with indexed errors', () => {
    const batch = validateBulkImportBatch([
      validInput,
      { ...validInput, sourcePath: '/books/second.pdf', format: 'pdf' },
      { ...validInput, sourcePath: '/books/broken.docx', format: 'docx' },
    ]);
    expect(batch.ok).toBe(false);
    expect(batch.results.filter((result) => result.ok)).toHaveLength(2);
    const failures = batch.results.filter((result) => !result.ok);
    expect(failures).toHaveLength(1);
    expect(failures[0]?.index).toBe(2);
  });

  it('rejects an empty batch without invoking', () => {
    const batch = validateBulkImportBatch([]);
    expect(batch.ok).toBe(false);
    expect(batch.key).toBe('import.validation.emptyBatch');
    expect(invokeMock).not.toHaveBeenCalled();
  });
});

describe('BulkImportService per-item gate', () => {
  it('imports only valid items and marks the invalid one failed', async () => {
    invokeMock.mockImplementation((command) => {
      if (command === 'importBook') {
        return Promise.resolve({
          id: `book-${Math.random()}`,
          title: 't',
          author: 'a',
          filePath: '/x',
          format: 'epub',
          syncStatus: 'synced',
          currentPage: 0,
          totalPages: 1,
          createdAt: '2026-01-01',
          updatedAt: '2026-01-01',
        });
      }
      return Promise.reject(new Error(`unexpected invoke: ${command}`));
    });

    const service = new BulkImportService({
      libraryPort: {
        scanFolder: async () => ({
          files: [
            { fullPath: '/books/a.epub', fileName: 'a.epub', format: 'epub', isDuplicate: false },
            { fullPath: '/books/b.pdf', fileName: 'b.pdf', format: 'pdf', isDuplicate: false },
            {
              fullPath: '/books/c.docx',
              fileName: 'c.docx',
              format: 'docx',
              isDuplicate: false,
            },
          ],
          skippedUnsupportedCount: 0,
          skippedUnreadableCount: 0,
        }),
        getFileBytes: async () => new Uint8Array(),
      } as unknown as LibraryPort,
    });

    const summary = await service.importFolder('/books');
    expect(summary.success).toBe(2);
    expect(summary.failed).toBe(1);
    const importCalls = invokeMock.mock.calls.filter(([command]) => command === 'importBook');
    expect(importCalls).toHaveLength(2);
    expect(importCalls.some(([, args]) => String(JSON.stringify(args)).includes('c.docx'))).toBe(
      false,
    );
  });

  it('rejects an empty scan with the empty-batch key', async () => {
    const service = new BulkImportService({
      libraryPort: {
        scanFolder: async () => ({
          files: [],
          skippedUnsupportedCount: 0,
          skippedUnreadableCount: 0,
        }),
        getFileBytes: async () => new Uint8Array(),
      } as unknown as LibraryPort,
    });
    const failure = await service.importFolder('/empty').catch((error) => error);
    expect(failure).toBeInstanceOf(ValidationError);
    expect((failure as ValidationError).key).toBe('import.validation.emptyBatch');
  });
});

describe('validateEpubMetadata', () => {
  it('flags malformed metadata missing title/author with an invalid date', () => {
    const result = validateEpubMetadata({ title: null, author: null, date: 'not-a-date' });
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.key).toBe('import.validation.invalidMetadata');
    }
    expect(validateEpubImportMetadataDto({ title: null, author: null, date: 'not-a-date' })).toBe(
      false,
    );
  });

  it('accepts a well-formed DTO and never throws on fallback-missing keys', () => {
    expect(
      validateEpubMetadata({ title: 'Dune', author: 'Frank Herbert', language: 'en' }).ok,
    ).toBe(true);
    expect(validateEpubImportMetadataDto(undefined)).toBe(false);
    expect(validateEpubImportMetadataDto(null)).toBe(false);
  });
});

describe('hand-rolled i18n keys', () => {
  it.each(['en', 'es'] as const)('resolves import and validation keys in %s', (locale) => {
    for (const key of [
      'import.emptyPath',
      'errors.importCommandFailed',
      'import.validation.invalidInput',
      'import.validation.emptyBatch',
      'import.validation.invalidItem',
      'import.validation.invalidMetadata',
    ] as const) {
      const message =
        key === 'import.validation.invalidItem'
          ? i18n.t(locale, key, { index: 1 })
          : i18n.t(locale, key);
      expect(message).not.toBe(key);
      expect(message.length).toBeGreaterThan(0);
    }
  });

  it('falls back to the raw message instead of throwing for unknown keys', () => {
    const result = validateBookImportInput({ sourcePath: '', format: 'nope' });
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(() => {
        throw new ValidationError(result.key, result.fallback);
      }).toThrow(result.fallback);
    }
  });
});
