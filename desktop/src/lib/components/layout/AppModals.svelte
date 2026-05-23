<script lang="ts">
  import { appState } from "$lib/stores/AppState.svelte";
  import EditMetadataModal from "$lib/components/library/EditMetadataModal.svelte";
  import CollectionManager from "$lib/components/library/CollectionManager.svelte";
  import BulkImportModal from "$lib/components/library/BulkImportModal.svelte";
  import ErrorToast from "$lib/components/ui/feedback/ErrorToast.svelte";
  import ErrorFallback from "$lib/components/ui/feedback/ErrorFallback.svelte";
  import DebugToggle from "$lib/debug/DebugToggle.svelte";
  import DebugPanel from "$lib/debug/DebugPanel.svelte";
</script>

<EditMetadataModal
  book={appState.editingBook as any}
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
<DebugToggle />
<DebugPanel />
