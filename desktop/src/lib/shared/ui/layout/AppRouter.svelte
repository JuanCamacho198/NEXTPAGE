<script lang="ts">
  import { appState } from '$lib/shared/stores/AppState.svelte';
  import { fly } from 'svelte/transition';

  import { Button } from '$lib/shared/ui';
  import { ShelfSection, ContinueReadingSection, AppSidebar } from '$lib/shared/ui';
  import { SettingsPanel } from '$lib/features/settings';
  import HomeDesktopView from '$lib/features/home/components/HomeDesktopView.svelte';
  import LibraryShelfScreen from '$lib/features/library/components/LibraryShelfScreen.svelte';
  import { ReaderWorkspace } from '$lib/features/reader';
  import HighlightsView from '$lib/features/highlights/components/HighlightsView.svelte';
  import { ReadingStatisticsView } from '$lib/features/stats';
  import WelcomeScreen from '$lib/features/welcome/WelcomeScreen.svelte';

  const showSidebar = $derived(appState.route !== 'reader' && appState.route !== 'welcome');
</script>

{#if showSidebar}
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

{#if !appState.isInitialized}
  <!--
    Brief loader: shown while the persisted auth cache is read. Prevents
    a flash of the welcome screen for returning users. ~150ms on a Tauri
    cold start; imperceptible.
  -->
  <main
    id="main-content"
    tabindex="-1"
    class="flex-1 overflow-y-auto relative flex items-center justify-center bg-(--color-background)"
  >
    <div
      role="status"
      aria-live="polite"
      class="flex flex-col items-center gap-3 text-(--color-text-muted)"
    >
      <div
        class="size-10 rounded-full border-2 border-(--color-border) border-t-(--color-primary) animate-spin"
        aria-hidden="true"
      ></div>
      <p class="m-0 text-sm">{appState.t('app.start')}...</p>
    </div>
  </main>
{:else if appState.route === 'welcome'}
  <!--
    Standalone welcome screen — no sidebar, no main wrapper, no fly
    transition. The screen is itself the entire viewport.
  -->
  <WelcomeScreen t={appState.t} />
{:else}
  <main
    id="main-content"
    tabindex="-1"
    class="flex-1 overflow-y-auto relative"
    class:p-4={appState.route !== 'reader'}
    class:md:p-6={appState.route !== 'reader'}
  >
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
      {#if appState.readerError}
        <p class="mb-3 rounded-lg border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-900">
          {appState.readerError}
        </p>
      {/if}

      {#key appState.route}
        {#if appState.route === 'home'}
          {@const previewBook = appState.getBookById(appState.previewBookId)}
          <div transition:fly={{ x: 0, y: 20, duration: 200, opacity: 0 }}>
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
                <Button
                  onclick={appState.handleImportFile}
                  disabled={appState.isImporting}
                  size="sm"
                >
                  {appState.isImporting
                    ? appState.t('app.importing')
                    : appState.t('app.importBook')}
                </Button>
              {/snippet}

              {#snippet continueSection()}
                <ContinueReadingSection />
              {/snippet}

              {#snippet shelfSection()}
                <ShelfSection />
              {/snippet}
            </HomeDesktopView>
          </div>
        {:else if appState.route === 'library'}
          <div transition:fly={{ x: 0, y: 20, duration: 200, opacity: 0 }}>
            <LibraryShelfScreen
              books={appState.books}
              isImporting={appState.isImporting}
              t={appState.t}
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
          </div>
        {:else if appState.route === 'stats'}
          <div transition:fly={{ x: 0, y: 20, duration: 200, opacity: 0 }}>
            <ReadingStatisticsView
              books={appState.books}
              stats={appState.stats}
              isLoading={appState.isLoadingStats}
              disabledReason={appState.statsUnavailableReason}
            />
          </div>
        {:else if appState.route === 'highlights'}
          <div transition:fly={{ x: 0, y: 20, duration: 200, opacity: 0 }}>
            <HighlightsView books={appState.books} t={appState.t} />
          </div>
        {:else if appState.route === 'settings'}
          <div transition:fly={{ x: 0, y: 20, duration: 200, opacity: 0 }}>
            <section class="space-y-3">
              <div class="flex justify-end">
                <Button size="sm" variant="ghost" onclick={appState.navigateToHome}
                  >{appState.t('app.backToHome')}</Button
                >
              </div>
              <SettingsPanel
                isOpen={true}
                mode="page"
                onRequestClose={appState.navigateToHome}
                t={appState.t}
                locale={appState.locale}
                onLocaleChange={appState.handleLocaleChange}
                onReaderSettingsChange={appState.handleReaderSettingsChange}
                books={appState.books.map((b) => ({ id: b.id, title: b.title }))}
              />
            </section>
          </div>
        {:else}
          <div transition:fly={{ x: 0, y: 20, duration: 200, opacity: 0 }}>
            <ReaderWorkspace
              activeReadingBook={appState.getBookById(appState.activeReadingBookId)}
              readerSettings={appState.readerSettings}
              percentage={appState.percentage}
              searchResponse={appState.searchResponse}
              searchTargetLocator={appState.searchTargetLocator}
              isSearching={appState.isSearching}
              searchUnavailableReason={appState.searchUnavailableReason}
              preloadedBytes={appState.preloadedBytes}
              t={appState.t}
              onBackToHome={appState.backToHome}
              onPdfPageChange={appState.handlePdfPageChange}
              onPdfSessionProgress={appState.handlePdfSessionProgress}
              onEpubLocationChange={appState.handleEpubLocationChange}
              onReaderLocationContext={appState.handleReaderLocationContext}
              onSearch={(query: string, page: number) => void appState.handleSearch(query, page)}
              onSearchJump={(target: unknown) =>
                void appState.handleSearchJump(
                  target as Parameters<typeof appState.handleSearchJump>[0],
                )}
            />
          </div>
        {/if}
      {/key}
    </div>
  </main>
{/if}
