export interface VersionedSyncRecord {
  recordId: string;
  updatedAtEpochMillis: number;
  deletedAtEpochMillis: number | null;
}

export interface StorageProvider {
  upload(id: string, file: Uint8Array): Promise<string>;
  download(remotePath: string): Promise<Uint8Array>;
  delete(remotePath: string): Promise<void>;
  list(prefix: string): Promise<string[]>;
}
