// ─── Feature state: stats — migrated to $state runes ───
// Canonical state is AppState ($lib/stores/AppState.svelte).

import type { ReadingStatsSummaryDto } from "$lib/shared/types";

class StatsStateManager {
  stats = $state<ReadingStatsSummaryDto | null>(null);
  isLoading = $state(false);
  unavailableReason = $state<string | null>(null);
}

export const statsState = new StatsStateManager();
