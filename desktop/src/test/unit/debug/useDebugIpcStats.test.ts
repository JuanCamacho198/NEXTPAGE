import { describe, it, expect } from 'vitest';
import {
  computeIpcSummary,
  computeP50,
  groupIpcCommands,
} from '$lib/shared/debug/useDebugIpcStats.svelte';

describe('useDebugIpcStats pure', () => {
  it('computeP50', () => {
    expect(computeP50([])).toBe(0);
    expect(computeP50([10])).toBe(10);
    expect(computeP50([10, 20, 30])).toBe(20);
    expect(computeP50([5, 15, 25, 35])).toBe(25); // floor(4/2)=2 => 25
  });

  it('computeIpcSummary', () => {
    const calls = [
      { durationMs: 10, success: true },
      { durationMs: 20, success: false },
      { durationMs: null, success: true } as never,
    ];
    const s = computeIpcSummary(calls as never);
    expect(s.totalCalls).toBe(3);
    expect(s.avgDuration).toBe(15);
    expect(s.maxDuration).toBe(20);
    expect(s.successRate).toBe(67);
  });

  it('groupIpcCommands with p50 and successRate sorted by count', () => {
    const calls = [
      { feature: 'a', durationMs: 10, success: true },
      { feature: 'a', durationMs: 30, success: true },
      { feature: 'b', durationMs: 5, success: false },
      { feature: 'a', durationMs: 20, success: true },
      { feature: 'b', durationMs: 15, success: true },
    ];
    const groups = groupIpcCommands(calls);
    expect(groups[0].feature).toBe('a');
    expect(groups[0].count).toBe(3);
    expect(groups[0].p50Duration).toBe(20);
    expect(groups[1].feature).toBe('b');
    expect(groups[1].count).toBe(2);
  });

  it('recentCalls slice -25 reverse pure', () => {
    const arr = Array.from({ length: 30 }, (_, i) => i);
    const recent = arr.slice(-25).reverse();
    expect(recent).toHaveLength(25);
    expect(recent[0]).toBe(29);
    expect(recent[24]).toBe(5);
  });
});
