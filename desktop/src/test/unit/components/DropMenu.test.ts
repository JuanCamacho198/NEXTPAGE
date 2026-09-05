import { render, screen } from '@testing-library/svelte';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import DropMenuHost from './DropMenuHost.svelte';

describe('DropMenu', () => {
  it('opens on trigger click and closes on Escape', async () => {
    const user = userEvent.setup();
    render(DropMenuHost);

    expect(screen.queryByTestId('dm-item')).not.toBeInTheDocument();
    await user.click(screen.getByTestId('dm-trigger'));
    expect(screen.getByTestId('dm-item')).toBeInTheDocument();

    await user.keyboard('{Escape}');
    expect(screen.queryByTestId('dm-item')).not.toBeInTheDocument();
  });

  it('dismisses after a menu item is selected', async () => {
    const user = userEvent.setup();
    render(DropMenuHost);

    await user.click(screen.getByTestId('dm-trigger'));
    expect(screen.getByTestId('dm-item')).toBeInTheDocument();

    await user.click(screen.getByTestId('dm-item'));
    expect(screen.queryByTestId('dm-item')).not.toBeInTheDocument();
  });

  it('toggles on repeated trigger clicks', async () => {
    const user = userEvent.setup();
    render(DropMenuHost);

    await user.click(screen.getByTestId('dm-trigger'));
    expect(screen.getByTestId('dm-item')).toBeInTheDocument();

    await user.click(screen.getByTestId('dm-trigger'));
    expect(screen.queryByTestId('dm-item')).not.toBeInTheDocument();
  });

  it('opens via Enter on the native button trigger without double-toggling', async () => {
    const user = userEvent.setup();
    render(DropMenuHost);

    await user.tab(); // focus the trigger button
    await user.keyboard('{Enter}');

    expect(screen.getByTestId('dm-item')).toBeInTheDocument();
  });
});
