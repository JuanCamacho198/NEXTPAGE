<script lang="ts">
  import type { CollectionDto } from '$lib/shared/types';
  import type { LibraryPort } from '$lib/shared/ports/LibraryPort';
  import { TauriLibraryAdapter } from '$lib/shared/ports/adapters/tauri/TauriLibraryAdapter';
  import { COLLECTION_COLOR_OPTIONS } from '../utils';
  import type { MessageKey } from '$lib/shared/i18n';

  let {
    open,
    onClose,
    t,
    libraryPort: libraryPortProp,
  }: {
    open: boolean;
    onClose: () => void;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    libraryPort?: LibraryPort;
  } = $props();

  // svelte-ignore state_referenced_locally
  const libraryPort: LibraryPort = libraryPortProp ?? new TauriLibraryAdapter();

  let collections = $state<CollectionDto[]>([]);
  let loading = $state(false);
  let newName = $state('');
  let newColor = $state('#6366f1');
  let editingId = $state<number | null>(null);
  let editName = $state('');

  async function loadCollections(): Promise<void> {
    loading = true;
    try {
      collections = await libraryPort.listCollections();
    } catch (e) {
      console.error('Failed to load collections:', e);
    } finally {
      loading = false;
    }
  }

  async function handleCreate(): Promise<void> {
    if (!newName.trim()) return;
    try {
      const created = await libraryPort.createCollection({ name: newName.trim(), color: newColor });
      collections = [...collections, created];
      newName = '';
      newColor = '#6366f1';
    } catch (e) {
      console.error('Failed to create collection:', e);
    }
  }

  async function handleDelete(id: number): Promise<void> {
    try {
      await libraryPort.deleteCollection(id);
      collections = collections.filter((c) => c.id !== id);
    } catch (e) {
      console.error('Failed to delete collection:', e);
    }
  }

  function cancelEdit(): void {
    editingId = null;
    editName = '';
  }

  $effect(() => {
    if (open) {
      loadCollections();
    }
  });
</script>

{#if open}
  <div
    class="fixed inset-0 z-50 flex items-center justify-center bg-black/50"
    role="presentation"
    onclick={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}
    onkeydown={(e) => {
      if (e.key === 'Escape') onClose();
    }}
  >
    <div
      class="w-full max-w-md rounded-xl border border-(--color-border) bg-(--color-surface) p-6 shadow-lg max-h-[80vh] overflow-y-auto"
      role="presentation"
      onkeydown={(e) => {
        if (e.key === 'Escape') onClose();
      }}
    >
      <div class="flex items-center justify-between mb-4">
        <h2 id="collection-manager-title" class="text-lg font-semibold text-(--color-primary)">
          {t('collection.managerTitle')}
        </h2>
        <button
          type="button"
          class="text-(--color-text-muted) hover:text-(--color-primary)"
          onclick={onClose}
          aria-label={t('collection.closeAria')}
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path
              stroke-linecap="round"
              stroke-linejoin="round"
              stroke-width="2"
              d="M6 18L18 6M6 6l12 12"
            />
          </svg>
        </button>
      </div>

      <div class="space-y-4">
        <div class="space-y-2">
          <h3 class="text-sm font-medium text-(--color-primary)">{t('collection.createNew')}</h3>
          <div class="flex gap-2">
            <input
              type="text"
              placeholder={t('collection.namePlaceholder')}
              class="flex-1 rounded-lg border border-(--color-border) bg-(--color-background) px-3 py-2 text-sm text-(--color-primary)"
              bind:value={newName}
            />
            <div class="flex gap-1">
              {#each COLLECTION_COLOR_OPTIONS as color}
                <button
                  type="button"
                  class="h-8 w-8 rounded-full transition-transform hover:scale-110"
                  style="background-color: {color}; {newColor === color
                    ? 'ring-2 ring-offset-2 ring-(--color-primary)'
                    : ''}"
                  onclick={() => (newColor = color)}
                  aria-label={t('collection.selectColor', { color })}
                ></button>
              {/each}
            </div>
          </div>
          <button
            type="button"
            class="w-full rounded-lg bg-(--color-primary) px-4 py-2 text-sm font-medium text-(--color-background) hover:opacity-90"
            onclick={handleCreate}
            disabled={!newName.trim()}
          >
            {t('collection.create')}
          </button>
        </div>

        <div class="border-t border-(--color-border) pt-4">
          <h3 class="text-sm font-medium text-(--color-primary) mb-2">{t('collection.existing')}</h3>
          {#if loading}
            <p class="text-sm text-(--color-text-muted)">{t('collection.loading')}</p>
          {:else if collections.length === 0}
            <p class="text-sm text-(--color-text-muted)">{t('collection.empty')}</p>
          {:else}
            <ul class="space-y-2">
              {#each collections as collection}
                <li class="flex items-center gap-2 rounded-lg border border-(--color-border) p-2">
                  {#if editingId === collection.id}
                    <input
                      type="text"
                      class="flex-1 rounded border border-(--color-border) bg-(--color-background) px-2 py-1 text-sm"
                      bind:value={editName}
                    />
                    <button
                      type="button"
                      class="text-(--color-primary) hover:opacity-80"
                      onclick={cancelEdit}
                    >
                      {t('collection.cancel')}
                    </button>
                  {:else}
                    <span
                      class="h-4 w-4 rounded-full"
                      style="background-color: {collection.color ?? '#6366f1'}"
                    ></span>
                    <span class="flex-1 text-sm text-(--color-primary)">{collection.name}</span>
                    {#if collection.isSystem}
                      <span class="text-xs text-(--color-text-muted)">{t('collection.system')}</span>
                    {:else}
                      <button
                        type="button"
                        class="text-xs text-(--color-text-muted) hover:text-(--color-error)"
                        onclick={() => handleDelete(collection.id)}
                      >
                        {t('collection.delete')}
                      </button>
                    {/if}
                  {/if}
                </li>
              {/each}
            </ul>
          {/if}
        </div>
      </div>
    </div>
  </div>
{/if}
