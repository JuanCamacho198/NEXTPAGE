<script lang="ts">
  import GoogleLoginButton from "./GoogleLoginButton.svelte";
  import Button from "./ui/Button.svelte";

  let { isOpen = $bindable(false) } = $props<{ isOpen: boolean }>();

  function closePanel() {
    isOpen = false;
  }
</script>

{#if isOpen}
  <!-- svelte-ignore a11y_click_events_have_key_events, a11y_no_static_element_interactions -->
  <div class="fixed inset-0 w-screen h-screen bg-black/40 z-[999]" onclick={closePanel}></div>
  <aside class="fixed top-0 right-0 w-[350px] h-screen bg-white border-l border-gray-200 shadow-xl z-[1000] flex flex-col animate-[slide-in_0.3s_ease-out]">
    <div class="flex items-center justify-between p-4 border-b border-gray-200">
      <h2 class="m-0 text-lg font-semibold text-gray-900">Settings</h2>
      <button class="bg-transparent border-none text-xl cursor-pointer text-gray-600 p-1 flex items-center justify-center hover:text-gray-900" onclick={closePanel} aria-label="Close settings">✕</button>
    </div>
    
    <div class="flex-1 overflow-y-auto p-4 flex flex-col gap-8">
      <div class="auth-section">
        <h3 class="mt-0 mb-2 text-base font-semibold text-gray-900">Authentication</h3>
        <p class="text-sm text-gray-600 mb-4">Sign in to sync your reading progress across devices.</p>
        <GoogleLoginButton />
      </div>

      <div class="about-section">
        <h3 class="mt-0 mb-2 text-base font-semibold text-gray-900">About NextPage</h3>
        <p class="text-sm text-gray-600">
          Version {typeof __APP_VERSION__ !== 'undefined' ? __APP_VERSION__ : '0.1.0'}
        </p>
      </div>
    </div>
  </aside>
{/if}

<style>
  @keyframes slide-in {
    from {
      transform: translateX(100%);
    }
    to {
      transform: translateX(0);
    }
  }
</style>