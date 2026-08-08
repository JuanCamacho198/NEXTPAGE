<script lang="ts">
  import Toast from './Toast.svelte';
  import { toastQueue, dismiss } from '$lib/stores/toastQueue.svelte';
</script>

{#each toastQueue.items as toast (toast.id)}
  <Toast
    type={toast.type}
    message={toast.message}
    visible={true}
    onDismiss={() => dismiss(toast.id)}
  />
{/each}
