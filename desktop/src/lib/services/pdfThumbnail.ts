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

export const generatePdfFirstPageThumbnail = async (filePath: string, maxWidth = 280) => {
  configureWorker();

  const fileData = await getFileBytes(filePath);
  const loadingTask = pdfjsLib.getDocument({
    data: new Uint8Array(fileData),
  });

  const pdfDoc = await loadingTask.promise;
  try {
    const page = await pdfDoc.getPage(1);
    const baseViewport = page.getViewport({ scale: 1 });
    const renderScale = baseViewport.width > maxWidth ? maxWidth / baseViewport.width : 1;
    const viewport = page.getViewport({ scale: renderScale });

    const canvas = document.createElement("canvas");
    canvas.width = Math.max(1, Math.floor(viewport.width));
    canvas.height = Math.max(1, Math.floor(viewport.height));

    const context = canvas.getContext("2d");
    if (!context) {
      throw new Error("Canvas 2D context is not available");
    }

    await page.render({
      canvasContext: context,
      viewport,
      canvas,
    }).promise;

    const blob = await new Promise<Blob>((resolve, reject) => {
      canvas.toBlob((result) => {
        if (!result) {
          reject(new Error("Failed to create thumbnail blob"));
          return;
        }
        resolve(result);
      }, "image/png");
    });

    return blobToBytes(blob);
  } finally {
    await pdfDoc.destroy();
    loadingTask.destroy();
  }
};
