<script lang="ts">
  import { readFile } from "@tauri-apps/plugin-fs";
  import { onDestroy } from "svelte";

  let { 
    path, 
    alt = "Cover", 
    className = "",
    fallback
  }: { 
    path: string; 
    alt?: string; 
    className?: string;
    fallback?: import("svelte").Snippet;
  } = $props();

  let objectUrl = $state<string | null>(null);
  let error = $state(false);

  const releaseObjectUrl = () => {
    if (!objectUrl) {
      return;
    }

    URL.revokeObjectURL(objectUrl);
    objectUrl = null;
  };

  async function loadThumbnail() {
    if (!path || path.trim().length === 0) {
      releaseObjectUrl();
      error = true;
      return;
    }
    
    try {
      const bytes = await readFile(path);
      if (!bytes || bytes.length === 0) {
        releaseObjectUrl();
        error = true;
        return;
      }

      const blob = new Blob([bytes], { type: "image/png" });

      releaseObjectUrl();
      
      objectUrl = URL.createObjectURL(blob);
      error = false;
    } catch (e) {
      console.error("[SafeCover] Failed to read cover file:", path, e);
      releaseObjectUrl();
      error = true;
    }
  }

  $effect(() => {
    loadThumbnail();
  });

  onDestroy(() => {
    releaseObjectUrl();
  });
</script>

{#if objectUrl && !error}
  <img
    src={objectUrl}
    {alt}
    class={className}
    onerror={() => {
      releaseObjectUrl();
      error = true;
    }}
  />
{:else if fallback}
  {@render fallback()}
{:else}
  <div class={`${className} flex items-center justify-center bg-gradient-to-br from-[#f2ede0] to-[#e4d5ba] text-[#5d4a33] border border-[var(--color-border,#d6d3ce)] font-bold`} role="img" aria-label={alt}>
    <span>{alt?.trim().slice(0, 1).toUpperCase() || "B"}</span>
  </div>
{/if}
