import type { VersionedSyncRecord } from '../services/storage/StorageProvider';

export interface ConflictResolver<T> {
  resolve(local: T | null | undefined, remote: T): T;
}

export class LastWriteWinsConflictResolver<T extends VersionedSyncRecord> implements ConflictResolver<T> {
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
