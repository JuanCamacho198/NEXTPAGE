<script lang="ts">
  import type { MessageKey } from "$lib/i18n";

  type Props = {
    title: string;
    bookProgress: number;
    currentPdfPage: number;
    totalPdfPages: number;
    isPdf: boolean;
    isFullscreen: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
  };

  let {
    title,
    bookProgress,
    currentPdfPage,
    totalPdfPages,
    isPdf,
    isFullscreen,
    t,
  }: Props = $props();
</script>

<footer class="flex h-12 shrink-0 items-center justify-between border-t border-[#1E293B] px-8" class:hidden={isFullscreen}>
  <span class="font-inter text-xs font-normal text-[#94A3B8]">
    {title}
  </span>
  {#if isPdf && totalPdfPages > 0}
    <div class="flex items-center gap-3">
      <span class="font-inter text-xs font-normal text-[#94A3B8]">{totalPdfPages - currentPdfPage} {t("pdf.pagesLeft")}</span>
      <div class="h-2 w-50 rounded-full bg-[#1E293B]">
        <div
          class="h-full rounded-full bg-[#38BDF8] transition-all duration-300"
          style="width: {Math.round((currentPdfPage / totalPdfPages) * 100)}%"
        ></div>
      </div>
      <span class="font-inter text-xs font-normal text-[#94A3B8]">{Math.round((currentPdfPage / totalPdfPages) * 100)}%</span>
    </div>
  {:else}
    <div class="flex items-center gap-3">
      <div class="h-2 w-50 rounded-full bg-[#1E293B]">
        <div
          class="h-full rounded-full bg-[#38BDF8] transition-all duration-300"
          style="width: {bookProgress}%"
        ></div>
      </div>
      <span class="font-inter text-xs font-normal text-[#94A3B8]">{bookProgress}%</span>
    </div>
  {/if}
</footer>
