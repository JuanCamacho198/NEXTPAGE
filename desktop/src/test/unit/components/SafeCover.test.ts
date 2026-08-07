/** PR5 SafeCover remote rendering — remote URL with browser cache, local fallback, failure never blocks. */
import { render, screen, waitFor } from '@testing-library/svelte';
import { readFile } from '@tauri-apps/plugin-fs';
import { describe, expect, it, vi } from 'vitest';
import SafeCover from '$lib/features/library/components/SafeCover.svelte';

vi.mock('@tauri-apps/api/core', () => ({ convertFileSrc: vi.fn((p: string) => `asset://localhost/${p}`) }));

vi.mock('@tauri-apps/plugin-fs', () => ({
  readFile: vi.fn(),
  BaseDirectory: { AppData: 0 },
}));

const mockedReadFile = readFile as unknown as ReturnType<typeof vi.fn>;

describe('SafeCover — remote cover references (PR5)', () => {
  it('renders a remote https URL directly without Tauri fs', async () => {
    render(SafeCover, { props: { path: 'https://cdn.example.com/covers/user-1/book-1/cover.jpg', alt: 'Cover' } });
    await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', 'https://cdn.example.com/covers/user-1/book-1/cover.jpg'));
    expect(mockedReadFile).not.toHaveBeenCalled();
  });

  it('falls back to the default letter cover when the remote image fails', async () => {
    const { container } = render(SafeCover, {
      props: { path: 'https://invalid.example.com/cover.jpg', alt: 'Cover' },
    });
    const img = container.querySelector('img');
    expect(img).not.toBeNull();
    img?.dispatchEvent(new Event('error'));
    await waitFor(() => expect(screen.getByRole('img', { name: 'Cover' })).toBeTruthy());
    expect(container.querySelector('img')).toBeNull();
  });

  it('still renders local file covers through Tauri fs when no remote reference', async () => {
    mockedReadFile.mockResolvedValue(new Uint8Array([137, 80, 78, 71]));
    const originalCreate = URL.createObjectURL;
    URL.createObjectURL = vi.fn(() => 'blob:local-cover') as unknown as typeof URL.createObjectURL;
    URL.revokeObjectURL = vi.fn() as unknown as typeof URL.revokeObjectURL;
    try {
      render(SafeCover, { props: { path: 'C:/covers/book-1.jpg', alt: 'Cover' } });
      await waitFor(() => expect(mockedReadFile).toHaveBeenCalledWith('C:/covers/book-1.jpg'));
      await waitFor(() => expect(screen.getByRole('img')).toHaveAttribute('src', 'blob:local-cover'));
    } finally {
      URL.createObjectURL = originalCreate;
    }
  });

  it('shows the default fallback when the local cover read fails (never blocks rendering)', async () => {
    mockedReadFile.mockRejectedValue(new Error('EACCES'));
    const { container } = render(SafeCover, { props: { path: 'C:/covers/missing.jpg', alt: 'Cover' } });
    expect(container.querySelector('img')).toBeNull();
    await waitFor(() => expect(screen.getByRole('img', { name: 'Cover' })).toBeTruthy());
  });
});
