import { invoke } from '@tauri-apps/api/core';
import { supabase } from '../supabase';
import { LastWriteWinsConflictResolver } from '../sync/ConflictResolver';
import { GDriveProvider } from './storage/GDriveProvider';
import type { VersionedSyncRecord } from './storage/StorageProvider';
import * as tauri from '../tauriClient';

export interface BookRecord extends VersionedSyncRecord {
  id: string;
  title: string;
  author: string;
  file_path: string;
  format: string;
  created_at: string;
  updated_at_iso: string;
  updatedAtEpochMillis: number;
  deletedAtEpochMillis: number | null;
}

export interface ProgressRecord extends VersionedSyncRecord {
  id: string;
  book_id: string;
  cfi_location: string;
  percentage: number;
  updated_at_iso: string;
  updatedAtEpochMillis: number;
  deletedAtEpochMillis: number | null;
}

export class SyncService {
  private static bookResolver = new LastWriteWinsConflictResolver<BookRecord>();
  private static progressResolver = new LastWriteWinsConflictResolver<ProgressRecord>();
  private static gdrive = new GDriveProvider();

  static async syncMetadata() {
    const { data: { session } } = await supabase.auth.getSession();
    if (!session) return;

    await Promise.all([
      this.syncBooks(session.user.id),
      this.syncProgress(session.user.id)
    ]);
  }

  private static async syncBooks(userId: string) {
    // 1. Fetch remote books from Supabase
    const { data: remoteBooks, error } = await supabase
      .from('books')
      .select('*')
      .eq('user_id', userId);

    if (error) throw error;

    // 2. Fetch local books from Tauri
    const localBooksRaw = await tauri.listBooks();
    const localBooks: BookRecord[] = localBooksRaw.map(b => ({
      id: b.id,
      recordId: b.id,
      title: b.title,
      author: b.author,
      file_path: b.filePath,
      format: b.format,
      created_at: b.createdAt,
      updated_at_iso: b.updatedAt,
      updatedAtEpochMillis: new Date(b.updatedAt).getTime(),
      deletedAtEpochMillis: null
    }));

    // 3. Resolve conflicts
    for (const remote of remoteBooks) {
      const remoteRecord: BookRecord = {
        id: remote.id,
        recordId: remote.id,
        title: remote.title,
        author: remote.author,
        file_path: remote.file_path,
        format: remote.format,
        created_at: remote.created_at,
        updated_at_iso: remote.updated_at,
        updatedAtEpochMillis: new Date(remote.updated_at).getTime(),
        deletedAtEpochMillis: remote.deleted_at ? new Date(remote.deleted_at).getTime() : null
      };

      const local = localBooks.find(b => b.id === remote.id);
      const resolved = this.bookResolver.resolve(local, remoteRecord);

      if (resolved === remoteRecord) {
        // Remote won or no local, update local metadata
        await tauri.upsertBook({
          id: resolved.id,
          title: resolved.title,
          author: resolved.author,
          filePath: resolved.file_path,
          format: resolved.format
        });

        // Trigger file sync if file is missing locally
        const existsLocally = await invoke<boolean>('file_exists', { path: resolved.file_path });
        if (!existsLocally) {
          try {
            console.log(`Syncing missing file for book: ${resolved.id}`);
            const fileData = await this.gdrive.download(resolved.id);
            await invoke('save_book_file', { id: resolved.id, data: Array.from(fileData) });
          } catch (e) {
            console.error(`Failed to sync book file for ${resolved.id}:`, e);
          }
        }
      } else if (local && resolved === local) {
        // Local won, update remote
        await supabase.from('books').upsert({
          id: local.id,
          user_id: userId,
          title: local.title,
          author: local.author,
          file_path: local.file_path,
          format: local.format,
          created_at: local.created_at,
          updated_at: local.updated_at_iso,
          deleted_at: local.deletedAtEpochMillis ? new Date(local.deletedAtEpochMillis).toISOString() : null
        });
      }
    }

    // 4. Push local-only books to remote
    const localOnly = localBooks.filter(lb => !remoteBooks.find(rb => rb.id === lb.id));
    for (const local of localOnly) {
      await supabase.from('books').upsert({
        id: local.id,
        user_id: userId,
        title: local.title,
        author: local.author,
        file_path: local.file_path,
        format: local.format,
        created_at: local.created_at,
        updated_at: local.updated_at_iso
      });
    }
  }

  private static async syncProgress(userId: string) {
    // Similar logic for reading_progress
    const { data: remoteProgress, error } = await supabase
      .from('reading_progress')
      .select('*')
      .eq('user_id', userId);

    if (error) throw error;

    const localBooks = await tauri.listBooks();
    for (const book of localBooks) {
      const localP = await tauri.getProgress(book.id);
      if (!localP) continue;

      const localRecord: ProgressRecord = {
        id: localP.id,
        recordId: localP.id,
        book_id: localP.bookId,
        cfi_location: localP.cfiLocation,
        percentage: localP.percentage,
        updated_at_iso: localP.updatedAt,
        updatedAtEpochMillis: new Date(localP.updatedAt).getTime(),
        deletedAtEpochMillis: null
      };

      const remote = remoteProgress.find(rp => rp.book_id === book.id);
      if (remote) {
        const remoteRecord: ProgressRecord = {
          id: remote.id,
          recordId: remote.id,
          book_id: remote.book_id,
          cfi_location: remote.cfi_location,
          percentage: remote.percentage,
          updated_at_iso: remote.updated_at,
          updatedAtEpochMillis: new Date(remote.updated_at).getTime(),
          deletedAtEpochMillis: remote.deleted_at ? new Date(remote.deleted_at).getTime() : null
        };

        const resolved = this.progressResolver.resolve(localRecord, remoteRecord);
        if (resolved === remoteRecord) {
          await tauri.upsertProgress({
            id: resolved.id,
            bookId: resolved.book_id,
            cfiLocation: resolved.cfi_location,
            percentage: resolved.percentage,
            updatedAt: resolved.updated_at_iso
          });
        } else {
          await supabase.from('reading_progress').upsert({
            id: localRecord.id,
            user_id: userId,
            book_id: localRecord.book_id,
            cfi_location: localRecord.cfi_location,
            percentage: localRecord.percentage,
            updated_at: localRecord.updated_at_iso
          });
        }
      } else {
        // Push local to remote
        await supabase.from('reading_progress').upsert({
          id: localRecord.id,
          user_id: userId,
          book_id: localRecord.book_id,
          cfi_location: localRecord.cfi_location,
          percentage: localRecord.percentage,
          updated_at: localRecord.updated_at_iso
        });
      }
    }

    // Pull remote-only progress
    const remoteOnly = remoteProgress.filter(rp => !localBooks.find(lb => lb.id === rp.book_id));
    for (const remote of remoteOnly) {
       await tauri.upsertProgress({
          id: remote.id,
          bookId: remote.book_id,
          cfiLocation: remote.cfi_location,
          percentage: remote.percentage,
          updatedAt: remote.updated_at
       });
    }
  }
}
