<script lang="ts">
  type Props = {
    text?: string;
    color?: string;
    class?: string;
  };

  let {
    text = "",
    color,
    class: className = "",
  }: Props = $props();

  const bgColor = $derived(color ?? "var(--color-primary)");
</script>

<span
  class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium {className}"
  style="background-color: {bgColor}; color: white;"
>
  {text}
</span>