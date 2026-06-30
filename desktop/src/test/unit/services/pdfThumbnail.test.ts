import { describe, expect, it, vi, beforeEach } from 'vitest';

vi.mock('$lib/shared/api/tauriClient', () => ({
  getFileBytes: vi.fn(),
}));

vi.mock('pdfjs-dist', () => {
  const getDocument = vi.fn();
  const getMetadata = vi.fn();
  const getPage = vi.fn();
  const destroy = vi.fn().mockResolvedValue(undefined);
  const numPages = 1;
  return {
    getDocument,
    GlobalWorkerOptions: { workerSrc: '' },
    __test: { getDocument, getMetadata, getPage, destroy, numPages },
  };
});

import { extractPdfMetadata } from '$lib/shared/services/pdfThumbnail';
import { getFileBytes } from '$lib/shared/api/tauriClient';
import * as pdfjsLib from 'pdfjs-dist';

type PdfjsTestHandle = {
  getDocument: ReturnType<typeof vi.fn>;
  getMetadata: ReturnType<typeof vi.fn>;
  getPage: ReturnType<typeof vi.fn>;
  destroy: ReturnType<typeof vi.fn>;
  numPages: number;
};

const handle = (pdfjsLib as unknown as { __test: PdfjsTestHandle }).__test;

const makeDoc = async (info: Record<string, unknown> | null): Promise<unknown> => {
  handle.getMetadata.mockResolvedValueOnce({ info });
  handle.getPage.mockResolvedValue({
    getViewport: () => ({ width: 100, height: 100 }),
    render: () => ({ promise: Promise.resolve() }),
  });
  return {
    numPages: handle.numPages,
    getMetadata: handle.getMetadata,
    getPage: handle.getPage,
    destroy: handle.destroy,
  };
};

describe('extractPdfMetadata subject extraction', () => {
  beforeEach(() => {
    vi.mocked(getFileBytes).mockReset();
    handle.getDocument.mockReset();
    handle.getMetadata.mockReset();
    handle.getPage.mockReset();
    handle.destroy.mockReset().mockResolvedValue(undefined);
    vi.mocked(getFileBytes).mockResolvedValue([1, 2, 3]);
    handle.getDocument.mockReturnValue({
      promise: Promise.resolve({ numPages: 1 }),
      destroy: handle.destroy,
    });
  });

  it('returns subject from info.Subject (canonical case)', async () => {
    const doc = await makeDoc({ Subject: 'Finanzas', Title: 'Rich Dad' });
    handle.getDocument.mockReturnValue({ promise: Promise.resolve(doc), destroy: handle.destroy });

    const meta = await extractPdfMetadata('/tmp/x.pdf');
    expect(meta.subject).toBe('Finanzas');
    expect(meta.title).toBe('Rich Dad');
  });

  it('accepts lowercase info.subject as a fallback', async () => {
    const doc = await makeDoc({ subject: 'Productividad' });
    handle.getDocument.mockReturnValue({ promise: Promise.resolve(doc), destroy: handle.destroy });

    const meta = await extractPdfMetadata('/tmp/x.pdf');
    expect(meta.subject).toBe('Productividad');
  });

  it('falls back to the first comma-separated Keywords token when Subject is empty', async () => {
    const doc = await makeDoc({ Subject: '', Keywords: 'Productividad, focus, habitos' });
    handle.getDocument.mockReturnValue({ promise: Promise.resolve(doc), destroy: handle.destroy });

    const meta = await extractPdfMetadata('/tmp/x.pdf');
    expect(meta.subject).toBe('Productividad');
  });

  it('returns null when both Subject and Keywords are missing or empty', async () => {
    const doc = await makeDoc({ Title: 'Book' });
    handle.getDocument.mockReturnValue({ promise: Promise.resolve(doc), destroy: handle.destroy });

    const meta = await extractPdfMetadata('/tmp/x.pdf');
    expect(meta.subject).toBeNull();
  });

  it('Subject takes priority over Keywords', async () => {
    const doc = await makeDoc({ Subject: 'Ficcion', Keywords: 'Other' });
    handle.getDocument.mockReturnValue({ promise: Promise.resolve(doc), destroy: handle.destroy });

    const meta = await extractPdfMetadata('/tmp/x.pdf');
    expect(meta.subject).toBe('Ficcion');
  });
});
