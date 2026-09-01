import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import MetricCard from '$lib/features/home/components/MetricCard.svelte';

const hexPattern = /#[0-9a-fA-F]{3,8}/;

describe('MetricCard', () => {
  it('renders label, value and icon glyph in an accent circle', () => {
    const { container } = render(MetricCard, {
      props: { label: 'Started', value: '12', icon: 'book' },
    });

    expect(screen.getByText('Started')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();

    const circle = container.querySelector('.h-9.w-9');
    expect(circle).not.toBeNull();
    expect(circle?.classList.contains('h-9')).toBe(true);
    expect(circle?.classList.contains('w-9')).toBe(true);
    expect(circle?.classList.contains('bg-(--color-accent-soft)')).toBe(true);
    expect(circle?.classList.contains('text-(--color-accent)')).toBe(true);
    expect(circle?.querySelector('svg')).not.toBeNull();
  });

  it('shows a skeleton placeholder while loading and hides the value', () => {
    const { container } = render(MetricCard, {
      props: { label: 'Started', value: '12', icon: 'book', isLoading: true },
    });

    expect(screen.queryByText('12')).not.toBeInTheDocument();
    expect(container.querySelector('.animate-pulse')).not.toBeNull();
    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull();
  });

  it('renders an accessible progress bar with clamped width', () => {
    const { container } = render(MetricCard, {
      props: { label: 'Daily goal', value: '10/20 min', icon: 'clock', progress: 0.5 },
    });

    const bar = container.querySelector('[role="progressbar"]');
    expect(bar).not.toBeNull();
    expect(bar).toHaveAttribute('aria-valuemin', '0');
    expect(bar).toHaveAttribute('aria-valuemax', '100');
    expect(bar).toHaveAttribute('aria-valuenow', '50');

    const fill = bar?.querySelector('div');
    expect(fill?.getAttribute('style')).toContain('width: 50%');
  });

  it('clamps progress outside the 0..1 range', () => {
    const { container } = render(MetricCard, {
      props: { label: 'Daily goal', value: 'x', icon: 'clock', progress: 1.7 },
    });
    expect(container.querySelector('[role="progressbar"]')).toHaveAttribute('aria-valuenow', '100');

    const { container: low } = render(MetricCard, {
      props: { label: 'Daily goal', value: 'x', icon: 'clock', progress: -0.4 },
    });
    expect(low.querySelector('[role="progressbar"]')).toHaveAttribute('aria-valuenow', '0');
  });

  it('renders the disabled reason instead of the value and hides the progress bar', () => {
    const { container } = render(MetricCard, {
      props: {
        label: 'Started',
        value: '12',
        icon: 'book',
        progress: 0.5,
        disabledReason: 'Stats unavailable.',
      },
    });

    expect(screen.getByText('Stats unavailable.')).toBeInTheDocument();
    expect(screen.queryByText('12')).not.toBeInTheDocument();
    expect(container.querySelector('[role="progressbar"]')).toBeNull();
  });

  it('contains no hardcoded hex colors', () => {
    const { container } = render(MetricCard, {
      props: { label: 'Started', value: '12', icon: 'book', progress: 0.4 },
    });

    expect(container.innerHTML).not.toMatch(hexPattern);
  });
});
