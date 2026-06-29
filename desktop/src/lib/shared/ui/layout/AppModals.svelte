<script lang="ts">
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { EditMetadataModal, CollectionManager, BulkImportModal } from '$lib/features/library';
  import ErrorToast from '$lib/shared/ui/feedback/ErrorToast.svelte';
  import ErrorFallback from '$lib/shared/ui/feedback/ErrorFallback.svelte';
</script>

<EditMetadataModal
  book={appState.editingBook as import('$lib/types').LibraryBookDto | null}
  open={appState.editingBook !== null}
  onClose={() => {
    appState.editingBook = null;
  }}
  onSave={appState.handleSaveEditedBook}
  t={appState.t}
/>

<CollectionManager
  open={appState.isCollectionManagerOpen}
  onClose={() => {
    appState.isCollectionManagerOpen = false;
  }}
/>

<BulkImportModal
  open={appState.isBulkImportOpen}
  folderName={appState.bulkImportFolderName}
  folderPath={appState.bulkImportFolderPath}
  scanResult={appState.bulkScanResult}
  isScanning={appState.isBulkScanning}
  scanError={appState.bulkScanError}
  isImporting={appState.isBulkImporting}
  importProgress={appState.bulkImportProgress}
  importSummary={appState.bulkImportSummary}
  onClose={appState.closeBulkImportModal}
  onPickFolder={appState.handlePickBulkImportFolder}
  onScan={appState.handleScanBulkImportFolder}
  onStartImport={appState.handleStartBulkImport}
  onCancelImport={appState.handleCancelBulkImport}
  t={appState.t}
/>

<ErrorToast />
<ErrorFallback />
