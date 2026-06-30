<script lang="ts">
  import type { Snippet } from 'svelte';
  import { profileSessionFromAuthState } from '$lib/features/settings/profileSession';
  import Avatar from '$lib/shared/ui/forms/Avatar.svelte';

  type Props = {
    actions?: Snippet;
  };

  let { actions }: Props = $props();

  // Read the profile from reactive auth state. Updates automatically
  // when the user signs in / out or refreshes their Google profile.
  const profile = $derived(profileSessionFromAuthState());
</script>

<section
  class="relative overflow-hidden rounded-[24px] border border-(--color-border) bg-[linear-gradient(180deg,rgba(18,30,47,0.92),rgba(12,20,32,0.84))] p-8 shadow-(--shadow-soft) backdrop-blur-xl"
>
  <!-- Decorative background glows -->
  <div
    class="pointer-events-none absolute -right-20 -top-20 h-64 w-64 rounded-full bg-(--color-accent-blue) opacity-[0.08] blur-[80px]"
  ></div>
  <div
    class="pointer-events-none absolute -bottom-20 -left-20 h-48 w-48 rounded-full bg-blue-500 opacity-[0.05] blur-[60px]"
  ></div>

  <div class="relative z-10 flex flex-col gap-6 md:flex-row md:items-center">
    <div class="flex items-center gap-5 min-w-0 flex-1">
      <Avatar
        src={profile.avatarUrl ?? undefined}
        name={profile.name}
        size="xl"
        class="border-(--color-border-strong)"
      />
      <div class="min-w-0">
        <h2 class="text-2xl font-bold tracking-tight text-(--color-primary)">
          {#if profile.isSignedIn}
            Hola, {profile.name}
          {:else}
            Hola
          {/if}
        </h2>
        <p class="mt-1 text-sm leading-relaxed text-(--color-text-muted)">
          Aquí tienes un resumen de tu progreso de lectura. Importa nuevos libros o retoma donde lo
          dejaste.
        </p>
      </div>
    </div>

    <div class="flex shrink-0 items-center gap-3">
      {#if actions}
        {@render actions()}
      {/if}
    </div>
  </div>
</section>
