<script lang="ts">
  import type { MessageKey } from "$lib/shared/i18n";

  type Props = {
    show: boolean;
    t: (key: MessageKey, params?: Record<string, string | number>) => string;
    onClose: () => void;
    onConfirm: () => void;
  };

  let { show, t, onClose, onConfirm }: Props = $props();
</script>

{#if show}
  <div class="fixed inset-0 bg-black/50 flex items-center justify-center z-[1001]">
    <div class="bg-(--color-surface,white) rounded-lg p-5 max-w-xs w-[90%] shadow-xl">
      <h3 class="mt-0 mb-2 text-base font-semibold text-zinc-900">{t("settings.resetConfirmTitle")}</h3>
      <p class="text-sm text-(--color-text-muted) mb-4">{t("settings.resetConfirmMessage")}</p>
      <div class="flex gap-2 justify-end">
        <button type="button" onclick={onClose} class="rounded-lg border border-(--color-border) px-4 py-1.5 text-sm bg-transparent text-(--color-primary) cursor-pointer hover:opacity-80">{t("settings.cancel")}</button>
        <button type="button" onclick={onConfirm} class="rounded-lg border border-red-500 px-4 py-1.5 text-sm bg-red-500 text-white cursor-pointer hover:opacity-90">{t("settings.reset")}</button>
      </div>
    </div>
  </div>
{/if}
