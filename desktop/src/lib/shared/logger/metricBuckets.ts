/**
 * Shared numeric bucketing helpers (metric-vocabulary spec: "bucketed at the
 * instrumentation site — not in the sink — so an unbucketed value cannot
 * exist"). Every numeric metric passes through one of these helpers BEFORE
 * egress; exact values never leave the process.
 */

/** Log-scale duration buckets (ms): upper bounds; last bucket is +Inf. */
export const DURATION_BUCKETS_MS: readonly number[] = [
  100, 250, 500, 1000, 2000, 4000, 8000, 16000, 32000, 64000,
];

/** Outbox-depth buckets: [lower, upper] pairs; 0 is its own bucket. */
const DEPTH_BUCKETS: ReadonlyArray<[number, number]> = [
  [0, 0],
  [1, 4],
  [5, 19],
  [20, 99],
  [100, Number.POSITIVE_INFINITY],
];

/** File/library size buckets in bytes: [lower, upper]; 0 is its own bucket. */
const SIZE_BUCKETS_BYTES: ReadonlyArray<[number, number]> = [
  [0, 0],
  [1, 5 * 1024 * 1024],
  [5 * 1024 * 1024, 20 * 1024 * 1024],
  [20 * 1024 * 1024, 100 * 1024 * 1024],
  [100 * 1024 * 1024, Number.POSITIVE_INFINITY],
];

/**
 * Bucket a duration to the upper bound of its log-scale bucket. A duration of
 * 1237 ms emits 2000, never the exact value.
 */
export function bucketDurationMs(ms: number): number {
  for (const bound of DURATION_BUCKETS_MS) {
    if (ms <= bound) return bound;
  }
  // Overflow bucket: emit the last finite edge, matching bucketDepth and
  // bucketSizeBytes. `Infinity` is not a valid value in a Sentry distribution and
  // would also diverge from the Kotlin mirror.
  return DURATION_BUCKETS_MS[DURATION_BUCKETS_MS.length - 1];
}

/** Bucket an outbox queue depth; emits the bucket upper bound (100+ → 100). */
export function bucketDepth(count: number): number {
  for (const [lower, upper] of DEPTH_BUCKETS) {
    if (count >= lower && count <= upper) {
      return Number.isFinite(upper) ? upper : 100;
    }
  }
  return 100;
}

/** Bucket a byte size; emits the bucket upper bound in bytes (100MB+ → 100MB). */
export function bucketSizeBytes(bytes: number): number {
  for (const [lower, upper] of SIZE_BUCKETS_BYTES) {
    if (bytes >= lower && bytes <= upper) {
      return Number.isFinite(upper) ? upper : 100 * 1024 * 1024;
    }
  }
  return 100 * 1024 * 1024;
}
