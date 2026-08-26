<script lang="ts">
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { libraryState } from '$lib/shared/stores/LibraryDomainState.svelte';
  import { bulkImportState } from '$lib/shared/stores/BulkImportDomainState.svelte';
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
  book={libraryState.editingBook as import('$lib/shared/types').LibraryBookDto | null}
  open={libraryState.editingBook !== null}
  onClose={() => {
    libraryState.editingBook = null;
  }}
  onSave={libraryState.handleSaveEditedBook}
  t={appState.t}
/>

<CollectionManager
  open={libraryState.isCollectionManagerOpen}
  onClose={() => {
    libraryState.isCollectionManagerOpen = false;
  }}
  t={appState.t}
/>

<BulkImportModal
  open={bulkImportState.isBulkImportOpen}
  folderName={bulkImportState.bulkImportFolderName}
  folderPath={bulkImportState.bulkImportFolderPath}
  scanResult={bulkImportState.bulkScanResult}
  isScanning={bulkImportState.isBulkScanning}
  scanError={bulkImportState.bulkScanError}
  isImporting={bulkImportState.isBulkImporting}
  importProgress={bulkImportState.bulkImportProgress}
  importSummary={bulkImportState.bulkImportSummary}
  onClose={bulkImportState.closeBulkImportModal}
  onPickFolder={() => bulkImportState.handlePickBulkImportFolder(appState.t('library.bulkImport.selectFolderTitle'))}
  onScan={bulkImportState.handleScanBulkImportFolder}
  onStartImport={bulkImportState.handleStartBulkImport}
  onCancelImport={bulkImportState.handleCancelBulkImport}
  t={appState.t}
/>

<ErrorToast />
<ErrorFallback />

<RemoveBookModal
  open={libraryState.pendingRemoveBook !== null}
  onClose={() => {
    libraryState.pendingRemoveBook = null;
  }}
  t={appState.t}
/>

<ToastHost />
