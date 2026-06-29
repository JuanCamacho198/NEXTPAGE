import { authState } from '$lib/stores/authState.svelte';

const DEFAULT_PROFILE_NAME = 'Reader';
const DEFAULT_PROFILE_EMAIL = 'No email available';

/** Lightweight user profile extracted from Google id_token or authState. */
export type GoogleUser = {
  email?: string | null;
  name?: string | null;
  picture?: string | null;
  sub?: string | null;
};

export type ProfileSessionViewModel = {
  name: string;
  email: string;
  avatarUrl: string | null;
  isSignedIn: boolean;
};

const toEmailLocalPart = (email: string | null | undefined): string | null => {
  if (typeof email !== 'string') {
    return null;
  }

  const normalized = email.trim();
  if (normalized.length === 0) {
    return null;
  }

  const [localPart] = normalized.split('@');
  return localPart?.trim().length ? localPart.trim() : null;
};

const toNonEmptyString = (value: unknown): string | null => {
  if (typeof value !== 'string') {
    return null;
  }

  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
};

const toValidHttpUrl = (value: unknown): string | null => {
  const candidate = toNonEmptyString(value);
  if (!candidate) {
    return null;
  }

  try {
    const parsed = new URL(candidate);
    if (parsed.protocol === 'http:' || parsed.protocol === 'https:') {
      return parsed.toString();
    }
  } catch {
    return null;
  }

  return null;
};

/**
 * Normalize a user profile from GoogleUser fields.
 * Reads email, name, and picture directly (flat structure, no user_metadata nesting).
 */
export const normalizeProfileSession = (
  user: GoogleUser | null | undefined,
): ProfileSessionViewModel => {
  const email = toNonEmptyString(user?.email) ?? DEFAULT_PROFILE_EMAIL;
  const localPart = toEmailLocalPart(user?.email);
  const name = toNonEmptyString(user?.name) ?? localPart ?? DEFAULT_PROFILE_NAME;

  return {
    name,
    email,
    avatarUrl: toValidHttpUrl(user?.picture),
    isSignedIn: Boolean(user?.email),
  };
};

/**
 * Convenience function that reads profile directly from reactive authState.
 * Use this for the new Google OAuth PKCE flow.
 */
export function profileSessionFromAuthState(): ProfileSessionViewModel {
  return normalizeProfileSession({
    email: authState.email,
    name: authState.displayName,
    picture: authState.photoUrl,
  });
}

export const getProfileInitials = (name: string): string => {
  const words = name
    .trim()
    .split(/\s+/)
    .filter((word) => word.length > 0);
  if (words.length === 0) {
    return DEFAULT_PROFILE_NAME[0];
  }

  if (words.length === 1) {
    return words[0].slice(0, 1).toUpperCase();
  }

  return `${words[0].slice(0, 1)}${words[1].slice(0, 1)}`.toUpperCase();
};
