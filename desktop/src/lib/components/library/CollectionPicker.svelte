<script lang="ts">
  import type { CollectionDto } from "$lib/types";
  import { addBookToCollection, removeBookFromCollection } from "$lib/api/tauriClient";

  type Props = {
    bookId: string;
    collectionIds: number[];
    collections: CollectionDto[];
    onUpdate: (newIds: number[]) => void;
  };

  let { bookId, collectionIds, collections, onUpdate }: Props = $props();

  let loading = $state(false);

  async function handleToggle(collectionId: number, checked: boolean) {
    if (loading) return;
    loading = true;
    try {
      if (checked) {
        await addBookToCollection({ bookId, collectionId });
      } else {
        await removeBookFromCollection({ bookId, collectionId });
      }
      const newIds = checked
        ? [...collectionIds, collectionId]
        : collectionIds.filter(id => id !== collectionId);
      onUpdate(newIds);
    } catch (e) {
      console.error("Failed to update collection:", e);
    } finally {
      loading = false;
    }
  }
</script>

<div class="space-y-2">
  {#each collections as collection}
    <label class="flex items-center gap-2 cursor-pointer">
      <input
        type="checkbox"
        checked={collectionIds.includes(collection.id)}
        disabled={loading}
        class="h-4 w-4 rounded border-[var(--color-border)] text-[var(--color-primary)] focus:ring-[var(--color-primary)]"
        onchange={(e) => handleToggle(collection.id, e.currentTarget.checked)}
      />
      <span
        class="inline-block h-3 w-3 rounded-full"
        style="background-color: {collection.color ?? '#6366f1'}"
      ></span>
      <span class="text-sm text-[var(--color-primary)]">{collection.name}</span>
    </label>
  {/each}
  {#if collections.length === 0}
    <p class="text-sm text-[var(--color-text-muted)]">No collections available</p>
  {/if}
</div>