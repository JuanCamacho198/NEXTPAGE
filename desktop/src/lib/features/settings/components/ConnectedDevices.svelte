<script lang="ts">
  import type { DeviceViewModel } from '$lib/services/devices'
  import type { MessageKey } from '$lib/shared/i18n'

  type Props = {
    devices: DeviceViewModel[]
    error: string | null
    isLoading: boolean
    currentDeviceId: string | null
    onremove: (id: string) => void
    t: (key: MessageKey, params?: Record<string, string | number>) => string
  }

  let {
    devices,
    error,
    isLoading,
    currentDeviceId,
    onremove,
    t,
  }: Props = $props()
</script>

{#if error}
  <p class="mb-3 rounded border border-amber-300 bg-amber-50 px-2 py-1 text-xs text-amber-900">
    {t('settings.connectedDevices.error')}
  </p>
{/if}

{#if isLoading && devices.length === 0}
  <!-- Don't render anything while loading if there are no devices yet -->
{:else if devices.length > 0}
  <div class="flex flex-col gap-2">
    {#each devices as device (device.id)}
      <div
        class="flex items-center gap-3 py-2 border-b border-(--color-border) last:border-b-0"
      >
        <!-- OS Icon -->
        {#if device.icon === 'windows'}
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            class="shrink-0 text-(--color-text-muted)"
            aria-hidden="true"
          >
            <rect x="3" y="3" width="8" height="8" rx="1" />
            <rect x="13" y="3" width="8" height="8" rx="1" />
            <rect x="3" y="13" width="8" height="8" rx="1" />
            <rect x="13" y="13" width="8" height="8" rx="1" />
          </svg>
        {:else if device.icon === 'apple'}
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            class="shrink-0 text-(--color-text-muted)"
            aria-hidden="true"
          >
            <path d="M12 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z" />
            <path d="M17 7c-1-1-3-1.5-5-1.5S8 6 7 7c-2 2-2.5 5-2.5 8 0 3 1 5 2.5 6 1 .5 2.5-.5 5-.5s4 1 5 .5c1.5-1 2.5-3 2.5-6 0-3-.5-6-2.5-8z" />
          </svg>
        {:else if device.icon === 'android'}
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            class="shrink-0 text-(--color-text-muted)"
            aria-hidden="true"
          >
            <path d="M6 9v6" />
            <path d="M18 9v6" />
            <rect x="4" y="9" width="16" height="10" rx="2" />
            <path d="M8 5l-2 4" />
            <path d="M16 5l2 4" />
          </svg>
        {:else}
          <!-- Linux icon -->
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
            class="shrink-0 text-(--color-text-muted)"
            aria-hidden="true"
          >
            <circle cx="12" cy="5" r="2" />
            <path d="M5 21c.5-2 2-4 7-4s6.5 2 7 4" />
            <path d="M8 14c.5-1 2-2 4-2s3.5 1 4 2" />
          </svg>
        {/if}

        <!-- Device info -->
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium text-(--color-primary) truncate">
              {device.name}
            </span>
            {#if device.isCurrent}
              <span
                class="shrink-0 px-1.5 py-0.5 rounded-md bg-(--color-accent-soft) text-(--color-accent-start) text-(--text-3xs) font-medium"
              >
                {t('settings.connectedDevices.thisDevice')}
              </span>
            {/if}
          </div>
          <div class="flex items-center gap-2 mt-0.5">
            <span class="text-(--text-3xs) text-(--color-text-muted) truncate">
              {device.os}
            </span>
            <span class="text-(--text-3xs) text-(--color-text-muted)">&middot;</span>
            <span class="text-(--text-3xs) text-(--color-text-muted)">
              {t('settings.connectedDevices.lastActive')}: {device.lastActive}
            </span>
          </div>
        </div>

        <!-- Remove button -->
        {#if !device.isCurrent}
          <button
            class="shrink-0 bg-transparent border-none p-0 text-xs text-red-500 cursor-pointer hover:text-red-600 transition-colors duration-150"
            onclick={() => onremove(device.id)}
          >
            {t('settings.connectedDevices.remove')}
          </button>
        {/if}
      </div>
    {/each}
  </div>
{/if}
