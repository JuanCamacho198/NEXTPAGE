<script lang="ts">
  import { theme, toggleTheme } from "../../../stores/theme";

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
  class="w-full flex items-center gap-[10px] rounded-[12px] p-[10px_12px] bg-transparent border border-(--color-border) text-(--color-text-muted) cursor-pointer transition-all duration-200 font-sans text-[13px] font-medium select-none -webkit-tap-highlight-transparent hover:bg-(--color-panel-accent) hover:border-(--color-border-strong) hover:text-(--color-primary) hover:shadow-(--shadow-soft) active:scale-[0.98]"
  class:animating
  onclick={handleToggle}
  aria-label={$theme === "dark" ? "Cambiar a tema claro" : "Cambiar a tema oscuro"}
  title={$theme === "dark" ? "Cambiar a tema claro" : "Cambiar a tema oscuro"}
>
  <div class="w-5 h-5 flex items-center justify-center shrink-0 relative">
    <!-- Sun icon (visible in light mode) -->
    <svg
      class="absolute transition-all duration-420 cubic-bezier(0.34,1.56,0.64,1) origin-center {$theme === 'light' ? 'opacity-100 scale-100 rotate-0' : 'opacity-0 scale-90 rotate-[90deg] pointer-events-none'} {animating ? 'animate-[icon-spin-in_0.42s_cubic-bezier(0.34,1.56,0.64,1)_forwards]' : ''}"
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <circle cx="12" cy="12" r="4" fill="currentColor" />
      <path d="M12 2v2M12 20v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M2 12h2M20 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
    </svg>

    <!-- Moon icon (visible in dark mode) -->
    <svg
      class="absolute transition-all duration-420 cubic-bezier(0.34,1.56,0.64,1) origin-center {$theme === 'dark' ? 'opacity-100 scale-100 rotate-0' : 'opacity-0 scale-90 rotate-[90deg] pointer-events-none'} {animating ? 'animate-[icon-spin-in_0.42s_cubic-bezier(0.34,1.56,0.64,1)_forwards]' : ''}"
      width="18"
      height="18"
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
    >
      <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z" fill="currentColor" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" />
    </svg>
  </div>

  <span class="flex-1 text-left text-(--color-primary)">{$theme === "dark" ? "Tema oscuro" : "Tema claro"}</span>
</button>