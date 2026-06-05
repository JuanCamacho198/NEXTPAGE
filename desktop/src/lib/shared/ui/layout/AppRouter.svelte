<script lang="ts">
  import { appState } from "$lib/shared/stores/AppState.svelte";

  import Button from "$lib/shared/ui/forms/Button.svelte";
  import ShelfSection from "$lib/shared/ui/layout/ShelfSection.svelte";
  import ContinueReadingSection from "$lib/shared/ui/layout/ContinueReadingSection.svelte";
  import { SettingsPanel } from "$lib/features/settings";
  import HomeDesktopView from "$lib/features/home/components/HomeDesktopView.svelte";
  import AppSidebar from "$lib/shared/ui/layout/AppSidebar.svelte";
  import LibraryShelfScreen from "$lib/features/library/components/LibraryShelfScreen.svelte";
  import ReaderWorkspace from "$lib/features/reader/components/ReaderWorkspace.svelte";
  import HighlightsView from "$lib/features/highlights/components/HighlightsView.svelte";
  import { ReadingStatisticsView } from "$lib/features/stats";
</script>

{#if appState.route !== "reader"}
  <AppSidebar
    activeRoute={appState.route}
    onNavigateHome={appState.navigateToHome}
    onNavigateLibrary={appState.navigateToLibrary}
    onNavigateStats={appState.navigateToStats}
    onNavigateHighlights={appState.navigateToHighlights}
    onNavigateSettings={appState.navigateToSettings}
    t={appState.t}
  />
{/if}

<div id="main-content" tabindex="-1" class="flex-1 overflow-y-auto relative" class:p-4={appState.route !== "reader"} class:md:p-6={appState.route !== "reader"}>
  <!-- aria-live region for screen reader announcements of dynamic content -->
  <div aria-live="polite" aria-atomic="true" class="sr-only">
    {#if appState.importProgress}
      {appState.importProgress.message}
    {/if}
    {#if appState.readerError}
      {appState.readerError}
    {/if}
  </div>
  <div class="mx-auto max-w-7xl">
    {#if appState.importProgress}
      <p class="mb-3 text-sm text-(--color-secondary)">{appState.importProgress.message}</p>
    {/if}

    {#if appState.readerError}
      <p class="mb-3 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900">{appState.readerError}</p>
    {/if}

    {#if appState.route === "home"}
      {@const previewBook = appState.getBookById(appState.previewBookId)}
      <HomeDesktopView
        stats={appState.stats}
        isLoadingStats={appState.isLoadingStats}
        statsUnavailableReason={appState.statsUnavailableReason}
        selectedBookTitle={previewBook?.title ?? null}
        continueCount={appState.continueReadingBooks.length}
        shelfCount={appState.myShelfBooks.length}
        statsMinutes={appState.stats?.totalMinutesRead ?? 0}
        activeRoute="home"
        onNavigateHome={appState.navigateToHome}
        onNavigateHighlights={appState.navigateToHighlights}
        onNavigateSettings={appState.navigateToSettings}
        onRefreshStats={() => {
          void appState.loadStats(appState.previewBookId ?? undefined);
        }}
        t={appState.t}
      >
        {#snippet navbarActions()}
          <Button onclick={appState.handleImportFile} disabled={appState.isImporting} size="sm">
            {appState.isImporting ? appState.t("app.importing") : appState.t("app.importBook")}
          </Button>
        {/snippet}

        {#snippet continueSection()}
          <ContinueReadingSection />
        {/snippet}

        {#snippet shelfSection()}
          <ShelfSection />
        {/snippet}
      </HomeDesktopView>
    {:else if appState.route === "library"}
      <LibraryShelfScreen
        books={appState.books}
        isImporting={appState.isImporting}
        onImportBook={appState.handleImportFile}
        onOpenBook={(book: Parameters<typeof appState.startReading>[0]) => {
          void appState.startReading(book);
        }}
        onContinueReading={(book: Parameters<typeof appState.startReading>[0]) => {
          void appState.startReading(book);
        }}
        onToggleFavorite={(book: Parameters<typeof appState.handleToggleFavorite>[0]) => {
          void appState.handleToggleFavorite(book);
        }}
        onMarkCompleted={(book: Parameters<typeof appState.handleMarkCompleted>[0]) => {
          void appState.handleMarkCompleted(book);
        }}
        onViewDetails={appState.openShelfDetails}
        onRemoveBook={(book: Parameters<typeof appState.handleHideBook>[0]) => {
          void appState.handleHideBook(book);
        }}
      />
    {:else if appState.route === "stats"}
      <ReadingStatisticsView
        books={appState.books}
        stats={appState.stats}
        isLoading={appState.isLoadingStats}
        disabledReason={appState.statsUnavailableReason}
      />
    {:else if appState.route === "highlights"}
      <HighlightsView
        books={appState.books}
        t={appState.t}
      />
    {:else if appState.route === "settings"}
      <section class="space-y-3">
        <div class="flex justify-end">
          <Button size="sm" variant="ghost" onclick={appState.navigateToHome}>{appState.t("app.backToHome")}</Button>
        </div>
        <SettingsPanel
          isOpen={true}
          mode="page"
          onRequestClose={appState.navigateToHome}
          t={appState.t}
          locale={appState.locale}
          onLocaleChange={appState.handleLocaleChange}
          onReaderSettingsChange={appState.handleReaderSettingsChange}
          books={appState.books.map(b => ({ id: b.id, title: b.title }))}
        />
      </section>
    {:else}
      <ReaderWorkspace
        activeReadingBook={appState.getBookById(appState.activeReadingBookId)}
        readerSettings={appState.readerSettings}
        percentage={appState.percentage}
        searchResponse={appState.searchResponse}
        searchTargetLocator={appState.searchTargetLocator}
        isSearching={appState.isSearching}
        searchUnavailableReason={appState.searchUnavailableReason}
        readerError={appState.readerError}
        preloadedBytes={appState.preloadedBytes}
        t={appState.t}
        onBackToHome={appState.backToHome}
        onPdfPageChange={appState.handlePdfPageChange}
        onPdfSessionProgress={appState.handlePdfSessionProgress}
        onEpubLocationChange={appState.handleEpubLocationChange}
        onReaderLocationContext={appState.handleReaderLocationContext}
        onSearch={(query: string, page: number) => void appState.handleSearch(query, page)}
        onSearchJump={(target: unknown) => void appState.handleSearchJump(target as Parameters<typeof appState.handleSearchJump>[0])}
      />
    {/if}
  </div>
</div>
