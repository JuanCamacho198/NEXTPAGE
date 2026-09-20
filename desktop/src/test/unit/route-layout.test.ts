import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import type { AppRoute } from '$lib/shared/stores/HomeState';
import {
  DEFAULT_ROUTE_LAYOUT,
  FULL_LAYOUT_ROUTES,
  resolveRouteLayout,
  routeLayoutClass,
  routeUsesPadding,
} from '$lib/shared/ui/layout/routeLayout';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROUTER = resolve(HERE, '../../lib/shared/ui/layout/AppRouter.svelte');

describe('route layout policy — mapping', () => {
  it('marks the full-bleed routes full and the readable ones contained', () => {
    const full: AppRoute[] = ['discover', 'addons', 'settings', 'storage', 'sync'];
    const contained: AppRoute[] = ['home', 'stats', 'highlights', 'dictionary', 'library'];

    for (const route of full) expect(resolveRouteLayout(route)).toBe('full');
    for (const route of contained) expect(resolveRouteLayout(route)).toBe('contained');
    expect([...FULL_LAYOUT_ROUTES]).toEqual(full);
  });

  it('defaults any route without an explicit policy to contained', () => {
    expect(DEFAULT_ROUTE_LAYOUT).toBe('contained');
    expect(resolveRouteLayout('welcome')).toBe('contained');
    expect(resolveRouteLayout('reader')).toBe('contained');
    expect(resolveRouteLayout('brand-new-screen' as AppRoute)).toBe('contained');
  });

  it('gives each policy a distinct container class', () => {
    expect(routeLayoutClass('full')).toContain('max-w-none');
    expect(routeLayoutClass('contained')).toContain('max-w-7xl');
    expect(routeLayoutClass('full')).not.toBe(routeLayoutClass('contained'));
  });

  it('keeps the edge-to-edge routes unpadded and every other route padded', () => {
    for (const route of ['reader', 'settings', 'storage', 'sync'] as AppRoute[]) {
      expect(routeUsesPadding(route)).toBe(false);
    }
    for (const route of [
      'home',
      'library',
      'discover',
      'addons',
      'stats',
      'highlights',
      'dictionary',
    ] as AppRoute[]) {
      expect(routeUsesPadding(route)).toBe(true);
    }
  });
});

describe('route layout policy — router reads it', () => {
  it('selects the width class dynamically instead of naming routes', () => {
    const source = readFileSync(ROUTER, 'utf8');
    expect(source).toContain('resolveRouteLayout');
    expect(source).toContain('routeLayoutClass');
    expect(source).toContain('routeUsesPadding');
    // The padding decision is not an inline route-name condition either.
    expect(source).not.toContain("navigationState.route !== 'settings'");

    // The defect literal is gone: neither width class is hardcoded in the router.
    expect(source).not.toContain("'mx-auto max-w-7xl'");
    expect(source).not.toContain('max-w-none');
    // The width class is bound from the policy, not an inline ternary.
    expect(source).toContain('<div class={contentLayoutClass}>');
  });
});
