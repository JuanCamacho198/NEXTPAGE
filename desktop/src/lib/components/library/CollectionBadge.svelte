<script lang="ts">
  import type { CollectionDto } from "../../types";

  type Props = {
    collection: CollectionDto;
    onClick?: () => void;
  };

  let { collection, onClick }: Props = $props();

  const bgColor = collection.color ?? "#6366f1";
</script>

<button
  type="button"
  class="inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium transition-opacity hover:opacity-80"
  style="background-color: {bgColor}; color: white;"
  onclick={onClick}
>
  {collection.name}
</button>