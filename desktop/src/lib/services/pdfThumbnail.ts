import * as pdfjsLib from "pdfjs-dist";
import { getFileBytes } from "../tauriClient";

let workerConfigured = false;

const configureWorker = () => {
  if (workerConfigured) {
    return;
  }

  pdfjsLib.GlobalWorkerOptions.workerSrc = new URL(
    "pdfjs-dist/build/pdf.worker.min.mjs",
    import.meta.url,
  ).toString();
  workerConfigured = true;
};

const blobToBytes = async (blob: Blob) => {
  const buffer = await blob.arrayBuffer();
  return new Uint8Array(buffer);
};

export type PdfMetadata = {
  author: string | null;
  title: string | null;
  thumbnailBytes: Uint8Array | null;
};

/**
 * Loads the PDF once and extracts both metadata (author, title) and
 * a first-page thumbnail. Returns null values for fields that cannot
 * be determined.
 */
export const extractPdfMetadata = async (
  filePath: string,
  maxWidth = 280,
): Promise<PdfMetadata> => {
  configureWorker();

  const fileData = await getFileBytes(filePath);
  const loadingTask = pdfjsLib.getDocument({
    data: new Uint8Array(fileData),
  });

  const pdfDoc = await loadingTask.promise;
  try {
    // Extract textual metadata
    let author: string | null = null;
    let title: string | null = null;
    try {
      const meta = await pdfDoc.getMetadata();
      const info = meta?.info as Record<string, unknown> | null | undefined;
      if (info) {
        const rawAuthor = info["Author"] ?? info["author"];
        const rawTitle = info["Title"] ?? info["title"];
        if (typeof rawAuthor === "string" && rawAuthor.trim().length > 0) {
          author = rawAuthor.trim();
        }
        if (typeof rawTitle === "string" && rawTitle.trim().length > 0) {
          title = rawTitle.trim();
        }
      }
    } catch {
      // metadata is optional – continue without it
    }

    // Render first page thumbnail
    let thumbnailBytes: Uint8Array | null = null;
    try {
      const page = await pdfDoc.getPage(1);
      const baseViewport = page.getViewport({ scale: 1 });
      const renderScale = baseViewport.width > maxWidth ? maxWidth / baseViewport.width : 1;
      const viewport = page.getViewport({ scale: renderScale });

      const canvas = document.createElement("canvas");
      canvas.width = Math.max(1, Math.floor(viewport.width));
      canvas.height = Math.max(1, Math.floor(viewport.height));

      const context = canvas.getContext("2d");
      if (context) {
        await page.render({ canvasContext: context, viewport, canvas }).promise;
        const blob = await new Promise<Blob | null>((resolve) => {
          canvas.toBlob((result) => resolve(result), "image/png");
        });
        if (blob) {
          thumbnailBytes = await blobToBytes(blob);
        }
      }
    } catch {
      // thumbnail is optional – continue without it
    }

    return { author, title, thumbnailBytes };
  } finally {
    await pdfDoc.destroy();
    loadingTask.destroy();
  }
};

/** @deprecated Use extractPdfMetadata instead which is more efficient */
export const generatePdfFirstPageThumbnail = async (filePath: string, maxWidth = 280) => {
  const result = await extractPdfMetadata(filePath, maxWidth);
  if (!result.thumbnailBytes) {
    throw new Error("Failed to generate PDF thumbnail");
  }
  return result.thumbnailBytes;
};

