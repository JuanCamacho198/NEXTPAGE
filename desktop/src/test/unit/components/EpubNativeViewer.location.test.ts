import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

describe('EpubNativeViewer locator emission', () => {
  it('does not persist synthetic chapter locators and emits a precise CFI', () => {
    const source = readFileSync(
      join(process.cwd(), 'src/lib/features/reader/viewer-epub/useEpubBridge.svelte.ts'),
      'utf8',
    );
    expect(source).not.toContain('onLocationChange?.(`chapter:${index}`');
    expect(source).toContain('onLocationChange?.(preciseCfi');
    expect(source).toContain('locatorFromCfi(');
    expect(source).toContain('emitPreciseLocation');
  });
});
