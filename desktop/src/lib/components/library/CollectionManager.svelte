<script lang="ts">
  import type { CollectionDto, CreateCollectionInput } from "../../types";
  import { createCollection, deleteCollection, listCollections } from "../../tauriClient";

  type Props = {
    open: boolean;
    onClose: () => void;
  };

  let { open, onClose }: Props = $props();

  let collections = $state<CollectionDto[]>([]);
  let loading = $state(false);
  let newName = $state("");
  let newColor = $state("#6366f1");
  let editingId = $state<number | null>(null);
  let editName = $state("");
  let editColor = $state("");

  const colorOptions = [
    "#6366f1", "#8b5cf6", "#ec4899", "#ef4444", 
    "#f97316", "#eab308", "#22c55e", "#14b8a6", "#0ea5e9"
  ];

  async function loadCollections() {
    loading = true;
    try {
      collections = await listCollections();
    } catch (e) {
      console.error("Failed to load collections:", e);
    } finally {
      loading = false;
    }
  }

  async function handleCreate() {
    if (!newName.trim()) return;
    try {
      const created = await createCollection({ name: newName.trim(), color: newColor });
      collections = [...collections, created];
      newName = "";
      newColor = "#6366f1";
    } catch (e) {
      console.error("Failed to create collection:", e);
    }
  }

  async function handleDelete(id: number) {
    try {
      await deleteCollection(id);
      collections = collections.filter(c => c.id !== id);
    } catch (e) {
      console.error("Failed to delete collection:", e);
    }
  }

  function startEdit(collection: CollectionDto) {
    editingId = collection.id;
    editName = collection.name;
    editColor = collection.color ?? "#6366f1";
  }

  function cancelEdit() {
    editingId = null;
    editName = "";
    editColor = "";
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
    role="dialog"
    aria-modal="true"
    aria-labelledby="collection-manager-title"
    onclick={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}
  >
    <div class="w-full max-w-md rounded-xl border border-[color:var(--color-border)] bg-[var(--color-surface)] p-6 shadow-lg max-h-[80vh] overflow-y-auto">
      <div class="flex items-center justify-between mb-4">
        <h2 id="collection-manager-title" class="text-lg font-semibold text-[var(--color-primary)]">
          Manage Collections
        </h2>
        <button
          type="button"
          class="text-[var(--color-text-muted)] hover:text-[var(--color-text)]"
          onclick={onClose}
        >
          <svg class="h-5 w-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      <div class="space-y-4">
        <div class="space-y-2">
          <h3 class="text-sm font-medium text-[var(--color-primary)]">Create New Collection</h3>
          <div class="flex gap-2">
            <input
              type="text"
              placeholder="Collection name"
              class="flex-1 rounded-lg border border-[color:var(--color-border)] bg-[var(--color-background)] px-3 py-2 text-sm text-[var(--color-text)]"
              bind:value={newName}
            />
            <div class="flex gap-1">
              {#each colorOptions as color}
                <button
                  type="button"
                  class="h-8 w-8 rounded-full transition-transform hover:scale-110"
                  style="background-color: {color}; {newColor === color ? 'ring-2 ring-offset-2 ring-[var(--color-primary)]' : ''}"
                  onclick={() => newColor = color}
                ></button>
              {/each}
            </div>
          </div>
          <button
            type="button"
            class="w-full rounded-lg bg-[var(--color-primary)] px-4 py-2 text-sm font-medium text-[var(--color-background)] hover:opacity-90"
            onclick={handleCreate}
            disabled={!newName.trim()}
          >
            Create
          </button>
        </div>

        <div class="border-t border-[color:var(--color-border)] pt-4">
          <h3 class="text-sm font-medium text-[var(--color-primary)] mb-2">Existing Collections</h3>
          {#if loading}
            <p class="text-sm text-[var(--color-text-muted)]">Loading...</p>
          {:else if collections.length === 0}
            <p class="text-sm text-[var(--color-text-muted)]">No collections yet</p>
          {:else}
            <ul class="space-y-2">
              {#each collections as collection}
                <li class="flex items-center gap-2 rounded-lg border border-[color:var(--color-border)] p-2">
                  {#if editingId === collection.id}
                    <input
                      type="text"
                      class="flex-1 rounded border border-[color:var(--color-border)] bg-[var(--color-background)] px-2 py-1 text-sm"
                      bind:value={editName}
                    />
                    <button
                      type="button"
                      class="text-[var(--color-primary)] hover:opacity-80"
                      onclick={cancelEdit}
                    >
                      Cancel
                    </button>
                  {:else}
                    <span
                      class="h-4 w-4 rounded-full"
                      style="background-color: {collection.color ?? '#6366f1'}"
                    ></span>
                    <span class="flex-1 text-sm text-[var(--color-primary)]">{collection.name}</span>
                    {#if collection.isSystem}
                      <span class="text-xs text-[var(--color-text-muted)]">System</span>
                    {:else}
                      <button
                        type="button"
                        class="text-xs text-[var(--color-text-muted)] hover:text-[var(--color-error)]"
                        onclick={() => handleDelete(collection.id)}
                      >
                        Delete
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