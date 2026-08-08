<script lang="ts">
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import {
    EditMetadataModal,
    CollectionManager,
    BulkImportModal,
    RemoveBookModal,
  } from '$lib/features/library';
  import ErrorToast from '$lib/shared/ui/feedback/ErrorToast.svelte';
  import ErrorFallback from '$lib/shared/ui/feedback/ErrorFallback.svelte';
  import ToastHost from '$lib/shared/ui/feedback/ToastHost.svelte';
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
  t={appState.t}
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

<!-- 2-step removal modal: "Local only" vs "Local + Drive" (REQ-09/10/11) -->
<RemoveBookModal
  open={appState.pendingRemoveBook !== null}
  onClose={() => {
    appState.pendingRemoveBook = null;
  }}
  t={appState.t}
/>

<!-- Mounted outside AppRouter: toasts survive route changes (REQ-05) -->
<ToastHost />
