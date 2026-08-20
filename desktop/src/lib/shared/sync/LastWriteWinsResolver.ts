import type { VersionedSyncRecord } from '../services/storage/StorageProvider';

export interface ConflictResolver<T> {
  resolve(local: T | null | undefined, remote: T): T;
}

/**
 * LastWriteWinsResolver — LWW with tombstone support.
 *
 * - Tombstone: `deletedAtEpochMillis` comparison. Any deleted row beats a
 *   non-deleted row; both deleted → later `deletedAt` wins; tie → local.
 * - Latest: `remote.updatedAtEpochMillis > local` → remote; `<` → local;
 *   `==` → `recordId` lexicographic (remote > local → remote else local).
 *
 * Canonical for all 5 tables: reading_progress, highlights, bookmarks,
 * reading_sessions, user_books. Clock skew risk mitigated by server `now()`
 * + `version` on the write path.
 */
export class LastWriteWinsResolver<T extends VersionedSyncRecord>
  implements ConflictResolver<T>
{
  resolve(local: T | null | undefined, remote: T): T {
    if (!local) return remote;

    if (local.deletedAtEpochMillis !== null || remote.deletedAtEpochMillis !== null) {
      return this.resolveTombstone(local, remote);
    }

    return this.chooseLatest(local, remote);
  }

  private resolveTombstone(local: T, remote: T): T {
    const localDeletedAt = local.deletedAtEpochMillis;
    const remoteDeletedAt = remote.deletedAtEpochMillis;

    if (localDeletedAt !== null && remoteDeletedAt === null) return local;
    if (localDeletedAt === null && remoteDeletedAt !== null) return remote;
    if (localDeletedAt !== null && remoteDeletedAt !== null) {
      return localDeletedAt >= remoteDeletedAt ? local : remote;
    }
    return this.chooseLatest(local, remote);
  }

  private chooseLatest(local: T, remote: T): T {
    if (remote.updatedAtEpochMillis > local.updatedAtEpochMillis) return remote;
    if (remote.updatedAtEpochMillis < local.updatedAtEpochMillis) return local;
    return remote.recordId > local.recordId ? remote : local;
  }
}

/** Backward-compatible alias — prefer `LastWriteWinsResolver`. */
export const LastWriteWinsConflictResolver = LastWriteWinsResolver;
