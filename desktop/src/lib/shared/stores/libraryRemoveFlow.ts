import { GDriveProvider } from '$lib/shared/services/storage/GDriveProvider';
import { SupabaseBookCatalogSync } from '$lib/shared/sync/SupabaseBookCatalogSync';
import { reportAuthError } from '$lib/shared/stores/syncAlert.svelte';
import { authState } from '$lib/shared/stores/AuthState.svelte';
import type { LibraryPort } from '$lib/shared/ports/LibraryPort';
import type { ReaderBook } from '$lib/shared/types';

type RemoveDeps = {
  libraryPort: LibraryPort;
  loadLibrary: () => Promise<void>;
  setReaderError: (msg: string) => void;
  gdrive?: { delete: (ref: string) => Promise<void> };
  catalogSync?: {
    fetchCatalog(): Promise<
      { id: string; remoteFileId?: string | null; remoteName?: string | null }[]
    >;
    tombstoneBook(id: string): Promise<void>;
  } | null;
};

async function resolveRemoteFileRef(
  bookId: string,
  catalogSync: NonNullable<RemoveDeps['catalogSync']>,
): Promise<string | null> {
  const rows = await catalogSync.fetchCatalog();
  const row = rows.find((e) => e.id === bookId);
  return row?.remoteFileId ?? row?.remoteName ?? null;
}

function messageFrom(error: unknown): string {
  const t = error as { commandError?: { message: string } };
  return t.commandError?.message ?? (error instanceof Error ? error.message : 'Unknown error');
}

export async function handleRemoveBookFromDrive(book: ReaderBook, deps: RemoveDeps): Promise<void> {
  const gdrive = deps.gdrive ?? new GDriveProvider();
  const catalogSync =
    deps.catalogSync !== undefined
      ? deps.catalogSync
      : authState.userId
        ? new SupabaseBookCatalogSync(authState.userId)
        : null;
  try {
    if (catalogSync) {
      const ref = await resolveRemoteFileRef(book.id, catalogSync);
      if (ref) await gdrive.delete(ref);
    }
  } catch (e) {
    reportAuthError(e);
    deps.setReaderError(messageFrom(e));
    throw e;
  }
  let tombstoneError: unknown = null;
  if (catalogSync)
    try {
      await catalogSync.tombstoneBook(book.id);
    } catch (e) {
      tombstoneError = e;
    }
  let hideError: unknown = null;
  try {
    await deps.libraryPort.hideBook(book.id);
    await deps.loadLibrary();
  } catch (e) {
    hideError = e;
  }
  if (hideError) deps.setReaderError(messageFrom(hideError));
  else if (tombstoneError) deps.setReaderError(messageFrom(tombstoneError));
}
