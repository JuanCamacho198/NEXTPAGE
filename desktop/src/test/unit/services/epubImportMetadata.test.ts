import { describe, expect, it, vi, beforeEach } from 'vitest';

vi.mock('$lib/shared/api/tauriClient', () => ({
  getFileBytes: vi.fn(),
}));

import { getFileBytes } from '$lib/shared/api/tauriClient';
import { parseOpfDirectly } from '$lib/shared/services/epubImportMetadata';

const MOCK_FILE_PATH = '/tmp/fake-book.epub';

// Build a minimal "OPF" byte payload by encoding a string. Real EPUBs are
// zips, but the OPF fallback scans the raw bytes as text and runs a regex
// against the <metadata>...</metadata> block. As long as the metadata
// text is present in the bytes (which is the case inside a real zip's
// stored OPF), the regex finds it.
const encodeOpf = (opf: string): number[] => {
  const bytes = new TextEncoder().encode(opf);
  return Array.from(bytes);
};

const OPF_WITH_DC = `<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/">
  <metadata>
    <dc:title>Don Quijote de la Mancha</dc:title>
    <dc:creator>Miguel de Cervantes</dc:creator>
    <dc:subject>Novela</dc:subject>
  </metadata>
  <manifest/>
  <spine/>
</package>`;

const OPF_EPUB2_STYLE = `<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/">
  <metadata>
    <dc11:title>1984</dc11:title>
    <dc11:creator>George Orwell</dc11:creator>
  </metadata>
  <manifest/>
  <spine/>
</package>`;

const OPF_NO_METADATA = `<?xml version="1.0"?>
<package>
  <metadata/>
  <manifest/>
  <spine/>
</package>`;

const OPF_WITH_MULTIPLE_SUBJECTS = `<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/">
  <metadata>
    <dc:title>Habitos Atomicos</dc:title>
    <dc:creator>James Clear</dc:creator>
    <dc:subject>Desarrollo personal</dc:subject>
    <dc:subject>Habitos</dc:subject>
    <dc:subject>Productividad</dc:subject>
  </metadata>
  <manifest/>
  <spine/>
</package>`;

const OPF_WITH_DC11_SUBJECT = `<?xml version="1.0"?>
<package xmlns="http://www.idpf.org/2007/opf" xmlns:dc11="http://purl.org/dc/elements/1.1/">
  <metadata>
    <dc11:title>1984</dc11:title>
    <dc11:creator>George Orwell</dc11:creator>
    <dc11:subject>Ficcion</dc11:subject>
  </metadata>
  <manifest/>
  <spine/>
</package>`;

const OPF_WITH_WHITESPACE = `<?xml version="1.0"?>
<package>
  <metadata>
    <dc:title>  Dune   </dc:title>
    <dc:creator>Frank Herbert</dc:creator>
    <dc:identifier>urn:uuid:xxx</dc:identifier>
  </metadata>
</package>`;

describe('epubImportMetadata OPF fallback (regex parser)', () => {
  beforeEach(() => {
    vi.mocked(getFileBytes).mockReset();
  });

  it('extracts title and author from a standard EPUB 3 OPF (dc:title, dc:creator)', async () => {
    vi.mocked(getFileBytes).mockResolvedValue(encodeOpf(OPF_WITH_DC));
    const meta = await parseOpfDirectly(MOCK_FILE_PATH);
    expect(meta.title).toBe('Don Quijote de la Mancha');
    expect(meta.author).toBe('Miguel de Cervantes');
    expect(meta.subject).toBe('Novela');
    expect(meta.subjects).toEqual(['Novela']);
  });

  it('captures the first dc:subject and lists every subject in document order', async () => {
    vi.mocked(getFileBytes).mockResolvedValue(encodeOpf(OPF_WITH_MULTIPLE_SUBJECTS));
    const meta = await parseOpfDirectly(MOCK_FILE_PATH);
    expect(meta.subject).toBe('Desarrollo personal');
    expect(meta.subjects).toEqual([
      'Desarrollo personal',
      'Habitos',
      'Productividad',
    ]);
  });

  it('accepts any single-word namespace prefix on subject (e.g. dc11:subject)', async () => {
    vi.mocked(getFileBytes).mockResolvedValue(encodeOpf(OPF_WITH_DC11_SUBJECT));
    const meta = await parseOpfDirectly(MOCK_FILE_PATH);
    expect(meta.subject).toBe('Ficcion');
    expect(meta.subjects).toEqual(['Ficcion']);
  });

  it('trims whitespace from extracted values', async () => {
    vi.mocked(getFileBytes).mockResolvedValue(encodeOpf(OPF_WITH_WHITESPACE));
    const meta = await parseOpfDirectly(MOCK_FILE_PATH);
    expect(meta.title).toBe('Dune');
    expect(meta.author).toBe('Frank Herbert');
  });

  it('accepts any single-word namespace prefix (e.g. dc11:title)', async () => {
    vi.mocked(getFileBytes).mockResolvedValue(encodeOpf(OPF_EPUB2_STYLE));
    const meta = await parseOpfDirectly(MOCK_FILE_PATH);
    expect(meta.title).toBe('1984');
    expect(meta.author).toBe('George Orwell');
  });

  it('returns nulls when the OPF has no metadata block', async () => {
    vi.mocked(getFileBytes).mockResolvedValue(encodeOpf(OPF_NO_METADATA));
    const meta = await parseOpfDirectly(MOCK_FILE_PATH);
    expect(meta.title).toBeNull();
    expect(meta.author).toBeNull();
    expect(meta.subject).toBeNull();
    expect(meta.subjects).toEqual([]);
  });
});
