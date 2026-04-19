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

  async function loadThumbnail() {
    if (!path) return;
    
    try {
      const bytes = await readFile(path);
      const blob = new Blob([bytes], { type: "image/png" });
      
      if (objectUrl) URL.revokeObjectURL(objectUrl);
      
      objectUrl = URL.createObjectURL(blob);
      error = false;
    } catch (e) {
      console.error("[SafeCover] Failed to read cover file:", path, e);
      error = true;
    }
  }

  $effect(() => {
    if (path) {
      loadThumbnail();
    }
  });

  onDestroy(() => {
    if (objectUrl) {
      URL.revokeObjectURL(objectUrl);
    }
  });
</script>

{#if objectUrl && !error}
  <img src={objectUrl} {alt} class={className} />
{:else if fallback}
  {@render fallback()}
{:else}
  <div class={className}></div>
{/if}
