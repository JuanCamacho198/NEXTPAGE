<script lang="ts">
  import { theme, toggleTheme } from "../../stores/theme";

  let animating = $state(false);

  function handleToggle() {
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
  class="theme-toggle"
  class:animating
  onclick={handleToggle}
  aria-label={$theme === "dark" ? "Cambiar a tema claro" : "Cambiar a tema oscuro"}
  title={$theme === "dark" ? "Cambiar a tema claro" : "Cambiar a tema oscuro"}
>
  <div class="icon-wrapper">
    <!-- Sun icon (visible in light mode) -->
    <svg
      class="sun-icon"
      class:hidden={$theme === "dark"}
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
      class="moon-icon"
      class:hidden={$theme === "light"}
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

  <span class="toggle-label">
    {$theme === "dark" ? "Tema oscuro" : "Tema claro"}
  </span>
</button>

<style>
  .theme-toggle {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 10px;
    border-radius: 12px;
    padding: 10px 12px;
    background: transparent;
    border: 1px solid var(--color-border);
    color: var(--color-text-muted);
    cursor: pointer;
    transition:
      background 0.2s ease,
      border-color 0.2s ease,
      color 0.2s ease,
      box-shadow 0.2s ease;
    font-family: var(--font-sans);
    font-size: 0.8125rem;
    font-weight: 500;
    user-select: none;
    -webkit-tap-highlight-color: transparent;
  }

  .theme-toggle:hover {
    background: var(--color-panel-accent);
    border-color: var(--color-border-strong);
    color: var(--color-primary);
    box-shadow: var(--shadow-glow);
  }

  .theme-toggle:active {
    transform: scale(0.98);
  }

  .icon-wrapper {
    width: 20px;
    height: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    flex-shrink: 0;
    position: relative;
  }

  .sun-icon,
  .moon-icon {
    position: absolute;
    transition:
      transform 0.42s cubic-bezier(0.34, 1.56, 0.64, 1),
      opacity 0.3s ease;
    transform-origin: center;
  }

  .sun-icon.hidden,
  .moon-icon.hidden {
    opacity: 0;
    transform: scale(0.4) rotate(90deg);
    pointer-events: none;
  }

  .sun-icon:not(.hidden),
  .moon-icon:not(.hidden) {
    opacity: 1;
    transform: scale(1) rotate(0deg);
  }

  /* Spin animation on click */
  .theme-toggle.animating .sun-icon:not(.hidden),
  .theme-toggle.animating .moon-icon:not(.hidden) {
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

  .toggle-label {
    flex: 1;
    text-align: left;
  }
</style>
