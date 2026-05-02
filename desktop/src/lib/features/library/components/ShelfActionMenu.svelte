<script lang="ts">
  import { onMount } from "svelte";
  import { LibraryState } from "../state";

  const {
    bookId,
    isFavorite,
    readLabel,
    editLabel,
    removeLabel,
    favoriteAddLabel,
    favoriteRemoveLabel,
    triggerLabel,
    onRead,
    onEdit,
    onRemove,
    onToggleFavorite,
  }: {
    bookId: string;
    isFavorite: boolean;
    readLabel: string;
    editLabel: string;
    removeLabel: string;
    favoriteAddLabel: string;
    favoriteRemoveLabel: string;
    triggerLabel: string;
    onRead?: () => void;
    onEdit: () => void;
    onRemove: () => void;
    onToggleFavorite: () => void;
  } = $props();

  const libState = new LibraryState();

  let isOpen = $state(false);
  let containerEl = $state<HTMLDivElement | null>(null);
  let triggerEl = $state<HTMLButtonElement | null>(null);
  let menuEl = $state<HTMLDivElement | null>(null);

  const getMenuButtons = () => {
    if (!menuEl) {
      return [] as HTMLButtonElement[];
    }

    return Array.from(menuEl.querySelectorAll<HTMLButtonElement>("[data-menu-item='true']"));
  };

  const focusItemAt = (index: number) => {
    const items = getMenuButtons();
    if (items.length === 0) {
      return;
    }

    const nextIndex = (index + items.length) % items.length;
    items[nextIndex]?.focus();
  };

  const closeMenu = (returnFocus: boolean) => {
    if (!isOpen) {
      return;
    }

    isOpen = false;
    if (returnFocus) {
      triggerEl?.focus();
    }
  };

  const openMenu = (focusFirstItem: boolean) => {
    if (isOpen) {
      return;
    }

    isOpen = true;
    if (focusFirstItem) {
      queueMicrotask(() => {
        focusItemAt(0);
      });
    }
  };

  const toggleMenu = () => {
    if (isOpen) {
      closeMenu(true);
      return;
    }

    openMenu(true);
  };

  const handleAction = (action: () => void) => {
    action();
    closeMenu(true);
  };

  const handleTriggerKeyDown = (event: KeyboardEvent) => {
    if (event.key === "ArrowDown") {
      event.preventDefault();
      openMenu(true);
      return;
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      openMenu(false);
      queueMicrotask(() => {
        const items = getMenuButtons();
        if (items.length === 0) {
          return;
        }
        items[items.length - 1]?.focus();
      });
      return;
    }

    if (event.key === "Enter" || event.key === " ") {
      event.preventDefault();
      toggleMenu();
    }
  };

  const handleMenuKeyDown = (event: KeyboardEvent) => {
    const items = getMenuButtons();
    if (items.length === 0) {
      return;
    }

    const activeIndex = items.findIndex((item) => item === document.activeElement);

    if (event.key === "Escape") {
      event.preventDefault();
      closeMenu(true);
      return;
    }

    if (event.key === "ArrowDown") {
      event.preventDefault();
      focusItemAt(activeIndex + 1);
      return;
    }

    if (event.key === "ArrowUp") {
      event.preventDefault();
      focusItemAt(activeIndex <= 0 ? items.length - 1 : activeIndex - 1);
      return;
    }

    if (event.key === "Home") {
      event.preventDefault();
      focusItemAt(0);
      return;
    }

    if (event.key === "End") {
      event.preventDefault();
      focusItemAt(items.length - 1);
      return;
    }

    if (event.key === "Tab") {
      closeMenu(false);
    }
  };

  const handleContainerMouseEnter = () => {
    openMenu(false);
  };

  const handleContainerMouseLeave = () => {
    if (containerEl?.contains(document.activeElement)) {
      return;
    }

    closeMenu(false);
  };

  const handleContainerFocusOut = (event: FocusEvent) => {
    const nextTarget = event.relatedTarget as Node | null;
    if (nextTarget && containerEl?.contains(nextTarget)) {
      return;
    }

    closeMenu(false);
  };

  onMount(() => {
    const handleDocumentPointerDown = (event: PointerEvent) => {
      if (!isOpen) {
        return;
      }

      if (containerEl && !containerEl.contains(event.target as Node)) {
        closeMenu(false);
      }
    };

    const handleDocumentKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        closeMenu(true);
      }
    };

    document.addEventListener("pointerdown", handleDocumentPointerDown, true);
    document.addEventListener("keydown", handleDocumentKeyDown, true);
    return () => {
      document.removeEventListener("pointerdown", handleDocumentPointerDown, true);
      document.removeEventListener("keydown", handleDocumentKeyDown, true);
    };
  });
</script>

<div
  bind:this={containerEl}
  role="group"
  aria-label={triggerLabel}
  class="relative inline-block"
  onmouseenter={handleContainerMouseEnter}
  onmouseleave={handleContainerMouseLeave}
  onfocusout={handleContainerFocusOut}
>
  <button
    bind:this={triggerEl}
    type="button"
    class="rounded-md border border-[color:var(--color-border)] bg-[var(--color-surface)] px-2 py-1 text-xs text-[var(--color-text-muted)]"
    aria-label={triggerLabel}
    aria-haspopup="menu"
    aria-expanded={isOpen}
    aria-controls={libState.getShelfMenuId(bookId)}
    data-testid={`shelf-actions-trigger-${bookId}`}
    onclick={toggleMenu}
    onkeydown={handleTriggerKeyDown}
  >
    ...
  </button>

  {#if isOpen}
    <div
      bind:this={menuEl}
      id={libState.getShelfMenuId(bookId)}
      role="menu"
      tabindex="-1"
      aria-label={triggerLabel}
      class="absolute right-0 z-10 mt-2 w-56 rounded-md bg-[var(--color-surface)] shadow-lg ring-1 ring-[var(--color-border)]"
      onkeydown={handleMenuKeyDown}
    >
      <div class="py-1">
        <button
          type="button"
          role="menuitem"
          tabindex="0"
          data-menu-item="true"
          class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
          onclick={() => {
            handleAction(onToggleFavorite);
          }}
        >
          {isFavorite ? favoriteRemoveLabel : favoriteAddLabel}
        </button>

        {#if onRead}
          <button
            type="button"
            role="menuitem"
            tabindex="0"
            data-menu-item="true"
            class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
            onclick={() => {
              handleAction(onRead);
            }}
          >
            {readLabel}
          </button>
        {/if}

        <button
          type="button"
          role="menuitem"
          tabindex="0"
          data-menu-item="true"
          class="w-full px-4 py-2 text-left text-sm text-[var(--color-primary)] hover:bg-[color:var(--color-border)]"
          onclick={() => {
            handleAction(onEdit);
          }}
        >
          {editLabel}
        </button>

        <button
          type="button"
          role="menuitem"
          tabindex="0"
          data-menu-item="true"
          class="w-full px-4 py-2 text-left text-sm text-red-700 hover:bg-[color:var(--color-border)]"
          onclick={() => {
            handleAction(onRemove);
          }}
        >
          {removeLabel}
        </button>
      </div>
    </div>
  {/if}
</div>
