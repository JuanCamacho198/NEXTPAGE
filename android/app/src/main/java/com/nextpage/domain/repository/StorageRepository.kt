package com.nextpage.domain.repository

/**
 * Storage maintenance over the app's internal book files.
 */
interface StorageRepository {
    /**
     * Deletes backing files no live book references (orphans left by deletes
     * whose sync-settle gate timed out, or by failed imports).
     *
     * Safety contract:
     *  - runs only after sync has settled (`SyncSettleGate`);
     *  - `*.part` files are in-flight downloads and are never candidates;
     *  - a file modified within the grace window is skipped, so a download
     *    that has just renamed into place is not swept mid-import;
     *  - only files under the managed `catalog`/`pdfs`/`epubs` directories are
     *    considered.
     *
     * @return the number of files deleted.
     */
    suspend fun sweepOrphanBookFiles(): Int
}
