import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import Icon, { LUCIDE_BY_NAME, type IconName } from '$lib/shared/ui/navigation/Icon.svelte';

describe('Icon compatibility shim', () => {
  it('keeps the frozen IconName union without removing members', () => {
    const expected: IconName[] = [
      'home',
      'library',
      'stats',
      'highlights',
      'settings',
      'book',
      'search',
      'grid',
      'close',
      'minimize',
      'maximize',
      'restore',
      'sun',
      'moon',
      'chevron-left',
      'chevron-right',
    ];
    for (const name of expected) {
      const { unmount } = render(Icon, { name });
      expect(document.body.querySelector('svg')).not.toBeNull();
      unmount();
    }
  });

  it('renders a lucide component for mapped names with the same size contract', () => {
    const { container } = render(Icon, { name: 'home', size: 'md' });
    const svg = container.querySelector('svg');
    expect(svg?.className.baseVal ?? svg?.getAttribute('class')).toContain('lucide-house');
    expect(svg?.getAttribute('class')).toContain('h-4');
    expect(svg?.getAttribute('width')).toBe('16');
  });

  it('renders the legacy path fallback unchanged for unmapped names', () => {
    const { container } = render(Icon, { name: 'close', size: 'md' });
    const svg = container.querySelector('svg');
    expect(svg?.getAttribute('class')).not.toContain('lucide');
    expect(svg?.querySelector('path')?.getAttribute('d')).toBe('M6 18L18 6M6 6l12 12');
  });

  it('hides decorative icons from assistive technology in both branches', () => {
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

  it('exposes a partial lucide map covering the sidebar nav names', () => {
    for (const name of [
      'home',
      'library',
      'stats',
      'highlights',
      'settings',
      'book',
      'search',
      'grid',
    ] as IconName[]) {
      expect(LUCIDE_BY_NAME[name]).toBeDefined();
    }
    expect(LUCIDE_BY_NAME['close']).toBeUndefined();
  });
});
