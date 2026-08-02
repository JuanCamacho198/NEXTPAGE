import { describe, expect, it } from 'vitest';
import { isCustomTitlebarPlatform } from '$lib/shared/utils/platform';

describe('isCustomTitlebarPlatform', () => {
  it.each([
    ['windows', true],
    ['Windows', false],
    ['macos', false],
    ['linux', false],
    ['', false],
    [undefined, false],
  ] as Array<[string | undefined, boolean]>)('returns %s for osType=%s', (osType, expected) => {
    expect(isCustomTitlebarPlatform(osType)).toBe(expected);
  });
});
