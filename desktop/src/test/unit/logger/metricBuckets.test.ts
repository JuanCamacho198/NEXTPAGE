import { describe, expect, it } from 'vitest';
import {
  DURATION_BUCKETS_MS,
  bucketDepth,
  bucketDurationMs,
  bucketSizeBytes,
} from '$lib/shared/logger/metricBuckets';
import { METRIC_TAG_KEYS, SHARED_METRIC_VOCABULARY, TAG_DOMAINS } from '$lib/shared/logger/metricTypes';

describe('metricBuckets', () => {
  it('buckets durations to log-scale upper bounds', () => {
    expect(bucketDurationMs(0)).toBe(100);
    expect(bucketDurationMs(99)).toBe(100);
    expect(bucketDurationMs(100)).toBe(100);
    expect(bucketDurationMs(101)).toBe(250);
    expect(bucketDurationMs(1237)).toBe(2000);
    expect(bucketDurationMs(64000)).toBe(64000);
    expect(bucketDurationMs(64001)).toBe(64000);
  });

  it('buckets depth to the documented count buckets', () => {
    expect(bucketDepth(0)).toBe(0);
    expect(bucketDepth(1)).toBe(4);
    expect(bucketDepth(3)).toBe(4);
    expect(bucketDepth(4)).toBe(4);
    expect(bucketDepth(5)).toBe(19);
    expect(bucketDepth(19)).toBe(19);
    expect(bucketDepth(20)).toBe(99);
    expect(bucketDepth(99)).toBe(99);
    expect(bucketDepth(100)).toBe(100);
    expect(bucketDepth(1000)).toBe(100);
  });

  it('buckets byte sizes to the documented MB buckets', () => {
    const MB = 1024 * 1024;
    expect(bucketSizeBytes(0)).toBe(0);
    expect(bucketSizeBytes(MB)).toBe(5 * MB);
    expect(bucketSizeBytes(5 * MB)).toBe(5 * MB);
    expect(bucketSizeBytes(6 * MB)).toBe(20 * MB);
    expect(bucketSizeBytes(150 * MB)).toBe(100 * MB);
  });

  it('exposes an explicit bucket list so an unbucketed value cannot exist', () => {
    expect(DURATION_BUCKETS_MS).toEqual([100, 250, 500, 1000, 2000, 4000, 8000, 16000, 32000, 64000]);
  });
});

describe('metric vocabulary', () => {
  it('contains the P0 names', () => {
    expect(SHARED_METRIC_VOCABULARY).toContain('app_cold_start');
    expect(SHARED_METRIC_VOCABULARY).toContain('reader_ttfp_native');
    expect(SHARED_METRIC_VOCABULARY).toContain('ipc_call');
  });

  it('exposes the tag contract keys and enumerated domains', () => {
    expect(METRIC_TAG_KEYS).toEqual([
      'source',
      'event',
      'feature',
      'format',
      'platform',
      'engine',
    ]);
    expect(TAG_DOMAINS.platform).toEqual(['desktop', 'android']);
    expect(TAG_DOMAINS.format).toEqual(['epub', 'pdf']);
  });
});
