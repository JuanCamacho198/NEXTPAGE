<script lang="ts">
  import { theme, toggleTheme } from '$lib/shared/stores/theme';

  let animating = $state(false);

  function handleToggle(): void {
    if (animating) return;
    animating = true;
    toggleTheme();
    setTimeout(() => {
      animating = false;
    }, 420);
  }
</script>

<button
  id="theme-toggle-btn"
  class="w-full flex items-center gap-2.5 rounded-xl px-3 py-2.5 bg-transparent border border-(--color-border) text-(--color-text-muted) cursor-pointer transition-all duration-200 text-[0.8125rem] font-medium select-none [-webkit-tap-highlight-color:transparent] hover:bg-(--color-panel-accent) hover:border-(--color-border-strong) hover:text-(--color-primary) hover:shadow-(--shadow-glow) active:scale-[0.98]"
  style="font-family: var(--font-sans);"
  class:animating
  onclick={handleToggle}
  aria-label={$theme === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'}
  title={$theme === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'}
>
  <div class="w-5 h-5 flex items-center justify-center shrink-0 relative">
    <!-- Sun icon (visible in light mode) -->
    <svg
      class="absolute transition-[opacity,transform] duration-420 [transition-timing-function:cubic-bezier(0.34,1.56,0.64,1)] origin-center"
      class:opacity-100={$theme === 'light'}
      class:opacity-0={$theme === 'dark'}
      class:scale-100={$theme === 'light'}
      class:scale-[0.4]={$theme === 'dark'}
      class:rotate-0={$theme === 'light'}
      class:rotate-90={$theme === 'dark'}
      class:pointer-events-none={$theme === 'dark'}
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" fill="currentColor" />
      <path
        d="M12 2v2M12 20v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M2 12h2M20 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
      />
    </svg>

    <!-- Moon icon (visible in dark mode) -->
    <svg
      class="absolute transition-[opacity,transform] duration-420 [transition-timing-function:cubic-bezier(0.34,1.56,0.64,1)] origin-center"
      class:opacity-100={$theme === 'dark'}
      class:opacity-0={$theme === 'light'}
      class:scale-100={$theme === 'dark'}
      class:scale-[0.4]={$theme === 'light'}
      class:rotate-0={$theme === 'dark'}
      class:rotate-90={$theme === 'light'}
      class:pointer-events-none={$theme === 'light'}
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <path
        d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"
        fill="currentColor"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
  </div>

  <span class="flex-1 text-left">
    {$theme === 'dark' ? 'Tema oscuro' : 'Tema claro'}
  </span>
</button>

<style>
  /* Spin animation on click */
  .animating .opacity-100,
  .animating .scale-100 {
    animation: icon-spin-in 0.42s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
  }

  @keyframes icon-spin-in {
    0% {
      transform: scale(0.3) rotate(-180deg);
      opacity: 0;
    }
    60% {
      opacity: 0.8;
    }
    100% {
      transform: scale(1) rotate(0deg);
      opacity: 1;
    }
  }
</style>