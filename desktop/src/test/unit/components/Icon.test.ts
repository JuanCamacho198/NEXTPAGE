import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import Icon, { LUCIDE_BY_NAME, type IconName } from '$lib/shared/ui/navigation/Icon.svelte';

/**
 * The frozen union, written as literals so `bun run check` fails if any entry
 * stops being an `IconName` member (slice 9 may not add or remove members).
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
  'chart',
  'search',
  'close',
  'menu',
  'sun',
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
  'fullscreen-enter',
  'fullscreen-exit',
  'arrow-right',
  'bookmark',
  'note',
  'add',
  'filter',
  'user',
  'database',
  'info',
  'calendar',
  'hand',
  'minimize',
  'maximize',
  'restore',
  'flame',
  'cloud-sync',
  'book-open',
  'book-text',
  'quote',
  'calendar-plus',
];

describe('Icon compatibility shim', () => {
  it('keeps the frozen 45-member IconName union without removing members', () => {
    expect(UNION).toHaveLength(45);
    for (const name of UNION) {
      const { unmount } = render(Icon, { name });
      expect(document.body.querySelector('svg'), name).not.toBeNull();
      unmount();
    }
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
    for (const name of ['home', 'close'] as IconName[]) {
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
