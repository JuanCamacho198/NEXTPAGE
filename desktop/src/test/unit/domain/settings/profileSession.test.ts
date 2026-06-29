import { describe, expect, it } from 'vitest';
import {
  getProfileInitials,
  normalizeProfileSession,
  type GoogleUser,
} from '$lib/features/settings';

describe('profileSession', () => {
  it('normalizes signed-in session values', () => {
    const viewModel = normalizeProfileSession({
      email: 'reader@example.com',
      name: 'Reader Name',
      picture: 'https://example.com/avatar.png',
    });

    expect(viewModel).toEqual({
      name: 'Reader Name',
      email: 'reader@example.com',
      avatarUrl: 'https://example.com/avatar.png',
      isSignedIn: true,
    });
  });

  it('falls back to email local-part when name is missing', () => {
    const viewModel = normalizeProfileSession({
      email: 'local-part@example.com',
    } as GoogleUser | null);

    expect(viewModel.name).toBe('local-part');
    expect(viewModel.email).toBe('local-part@example.com');
  });

  it('uses default placeholders when session is missing', () => {
    const viewModel = normalizeProfileSession(null);
    expect(viewModel).toEqual({
      name: 'Reader',
      email: 'No email available',
      avatarUrl: null,
      isSignedIn: false,
    });
  });

  it('rejects non-http avatar urls', () => {
    const viewModel = normalizeProfileSession({
      email: 'reader@example.com',
      picture: "javascript:alert('xss')",
    });

    expect(viewModel.avatarUrl).toBeNull();
  });

  it('builds profile initials defensively', () => {
    expect(getProfileInitials('Reader Name')).toBe('RN');
    expect(getProfileInitials('Reader')).toBe('R');
    expect(getProfileInitials('   ')).toBe('R');
  });
});
