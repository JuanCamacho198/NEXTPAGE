import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import Icon, { LUCIDE_BY_NAME, type IconName } from '$lib/shared/ui/navigation/Icon.svelte';

/**
 * The union, written as literals so `bun run check` fails if any entry stops
 * being an `IconName` member. Slice 10 removed the five names whose last
 * consumer migrated (`chart`, `sun`, `user`, `database`, `cloud-sync`); slice
 * 11 removed the six whose last consumer was the reader chrome (`close`,
 * `menu`, `fullscreen-enter`, `fullscreen-exit`, `arrow-right`, `bookmark`);
 * slice 12 removed the four whose last consumer was the dictionary
 * (`book-open`, `book-text`, `quote`, `calendar-plus`).
 * The count below is the recorded post-slice-12 membership.
 */
const UNION: IconName[] = [
  'home',
  'library',
  'stats',
  'highlights',
  'settings',
  'book',
  'check',
  'clock',
  'trend-up',
  'search',
  'moon',
  'chevron-left',
  'chevron-right',
  'copy',
  'edit',
  'trash',
  'more-vertical',
  'grid',
  'list',
  'more-dot',
  'note',
  'add',
  'filter',
  'info',
  'calendar',
  'hand',
  'minimize',
  'maximize',
  'restore',
  'flame',
];

describe('Icon compatibility shim', () => {
  it('pins the post-slice-12 union at 30 members and renders each', () => {
    expect(UNION).toHaveLength(30);
    for (const name of UNION) {
      const { unmount } = render(Icon, { name });
      expect(document.body.querySelector('svg'), name).not.toBeNull();
      unmount();
    }
  });

  it('no longer maps the five names whose last consumer migrated in slice 10', () => {
    for (const name of ['chart', 'sun', 'user', 'database', 'cloud-sync']) {
      expect(LUCIDE_BY_NAME, name).not.toHaveProperty(name);
    }
  });

  it('no longer maps the six names whose last consumer was the reader chrome', () => {
    for (const name of [
      'close',
      'menu',
      'fullscreen-enter',
      'fullscreen-exit',
      'arrow-right',
      'bookmark',
    ]) {
      expect(LUCIDE_BY_NAME, name).not.toHaveProperty(name);
    }
    expect(Object.keys(LUCIDE_BY_NAME)).toHaveLength(30);
  });

  it('no longer maps the four names whose last consumer was the dictionary', () => {
    for (const name of ['book-open', 'book-text', 'quote', 'calendar-plus']) {
      expect(LUCIDE_BY_NAME, name).not.toHaveProperty(name);
      expect(Object.keys(LUCIDE_BY_NAME)).not.toContain(name);
    }
    expect(Object.keys(LUCIDE_BY_NAME)).toHaveLength(30);
  });

  it('resolves every union member to a lucide component and never the legacy path', () => {
    for (const name of UNION) {
      const { container, unmount } = render(Icon, { name, size: 'md' });
      const svg = container.querySelector('svg');
      // `lucide-icon` is emitted by the lucide branch only; the legacy `paths`
      // fallback renders its own `<svg>` without any `lucide` class, so this
      // proves no member falls through to the paths record.
      expect(svg?.getAttribute('class'), name).toContain('lucide-icon');
      expect(svg?.getAttribute('viewBox'), name).toBe('0 0 24 24');
      expect(svg?.getAttribute('stroke'), name).toBe('currentColor');
      expect(svg?.getAttribute('stroke-width'), name).toBe('1.8');
      unmount();
    }
  });

  it('is total over the union, leaving no name to the legacy paths record', () => {
    for (const name of UNION) {
      expect(LUCIDE_BY_NAME[name], name).toBeDefined();
    }
    expect(Object.keys(LUCIDE_BY_NAME).sort()).toEqual([...UNION].sort());
  });

  it('renders a lucide component with the same size contract', () => {
    const { container } = render(Icon, { name: 'home', size: 'md' });
    const svg = container.querySelector('svg');
    expect(svg?.className.baseVal ?? svg?.getAttribute('class')).toContain('lucide-house');
    expect(svg?.getAttribute('class')).toContain('h-4');
    expect(svg?.getAttribute('width')).toBe('16');
  });

  it('hides decorative icons from assistive technology for lucide-backed names', () => {
    for (const name of ['home', 'settings'] as IconName[]) {
      const { container, unmount } = render(Icon, { name });
      expect(container.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
      expect(container.querySelector('[role="tooltip"]')).toBeNull();
      unmount();
    }
  });

  it('keeps the title tooltip contract for lucide-backed icons', () => {
    render(Icon, { name: 'home', title: 'Inicio' });
    expect(screen.getByRole('tooltip', { name: 'Inicio' })).toBeInTheDocument();
  });
});
