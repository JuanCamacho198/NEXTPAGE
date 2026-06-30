import * as pdfjsLib from 'pdfjs-dist';
import { getFileBytes } from '$lib/shared/api/tauriClient';

let workerConfigured = false;

const configureWorker = (): void => {
  if (workerConfigured) {
    return;
  }

  pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
    'pdfjs-dist/build/pdf.worker.min.mjs',
    import.meta.url,
  ).toString();
  workerConfigured = true;
};

const blobToBytes = async (blob: Blob): Promise<Uint8Array> => {
  const buffer = await blob.arrayBuffer();
  return new Uint8Array(buffer);
};

const pickFirstString = (info: Record<string, unknown>, keys: string[]): string | null => {
  for (const key of keys) {
    const value = info[key];
    if (typeof value === 'string') {
      const trimmed = value.trim();
      if (trimmed.length > 0) {
        return trimmed;
      }
    }
  }
  return null;
};

export type PdfMetadata = {
  author: string | null;
  title: string | null;
  subject: string | null;
  totalPages: number | null;
  thumbnailBytes: Uint8Array | null;
};

/**
 * Loads the PDF once and extracts metadata (author, title, total pages) and
 * a first-page thumbnail.
 */
export const extractPdfMetadata = async (
  filePath: string,
  maxWidth = 280,
): Promise<PdfMetadata> => {
  configureWorker();

  console.log(`[PdfMetadata] Analyzing file: ${filePath}`);
  const fileData = await getFileBytes(filePath);
  const loadingTask = pdfjsLib.getDocument({
    data: new Uint8Array(fileData),
  });

  const pdfDoc = await loadingTask.promise;
  const numPages = pdfDoc.numPages;

  try {
    // Extract textual metadata
    let author: string | null = null;
    let title: string | null = null;
    let subject: string | null = null;
    try {
      const meta = await pdfDoc.getMetadata();
      const info = meta?.info as Record<string, unknown> | null | undefined;

      console.log(`[PdfMetadata] Raw Info:`, info);

      if (info) {
        const rawAuthor = info['Author'] ?? info['author'] ?? info['Creator'] ?? info['creator'];
        const rawTitle = info['Title'] ?? info['title'];
        if (typeof rawAuthor === 'string' && rawAuthor.trim().length > 0) {
          author = rawAuthor.trim();
        }
        if (typeof rawTitle === 'string' && rawTitle.trim().length > 0) {
          title = rawTitle.trim();
        }
        // `Subject` wins over `Keywords` (the latter is a single
        // comma-separated string in practice). Both keys are checked
        // case-insensitively because some PDF producers emit lowercase.
        const rawSubject = pickFirstString(info, ['Subject', 'subject']);
        const rawKeywords = pickFirstString(info, ['Keywords', 'keywords']);
        if (rawSubject) {
          subject = rawSubject;
        } else if (rawKeywords) {
          const firstToken = rawKeywords.split(',')[0]?.trim() ?? '';
          subject = firstToken.length > 0 ? firstToken : null;
        }
      }
    } catch (e) {
      console.error(`[PdfMetadata] Meta extraction error:`, e);
    }

    // Render first page thumbnail
    let thumbnailBytes: Uint8Array | null = null;
    try {
      const page = await pdfDoc.getPage(1);
      const baseViewport = page.getViewport({ scale: 1 });
      const renderScale = baseViewport.width > maxWidth ? maxWidth / baseViewport.width : 1;
      const viewport = page.getViewport({ scale: renderScale });

      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.floor(viewport.width));
      canvas.height = Math.max(1, Math.floor(viewport.height));

      const context = canvas.getContext('2d');
      if (context) {
        await page.render({ canvasContext: context, viewport, canvas }).promise;
        const blob = await new Promise<Blob | null>((resolve) => {
          canvas.toBlob((result) => resolve(result), 'image/png');
        });
        if (blob) {
          thumbnailBytes = await blobToBytes(blob);
          console.log(`[PdfMetadata] Thumbnail generated: ${thumbnailBytes.length} bytes`);
        }
      }
    } catch (e) {
      console.error(`[PdfMetadata] Thumbnail render error:`, e);
    }

    return { author, title, subject, totalPages: numPages, thumbnailBytes };
  } finally {
    await pdfDoc.destroy();
    loadingTask.destroy();
  }
};

/** @deprecated Use extractPdfMetadata instead which is more efficient */
export const generatePdfFirstPageThumbnail = async (
  filePath: string,
  maxWidth = 280,
): Promise<Uint8Array> => {
  const result = await extractPdfMetadata(filePath, maxWidth);
  if (!result.thumbnailBytes) {
    throw new Error('Failed to generate PDF thumbnail');
  }
  return result.thumbnailBytes;
};
