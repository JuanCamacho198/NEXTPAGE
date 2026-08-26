import { authState, type LocalUserProfile } from '$lib/shared/stores/AuthState.svelte';

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

/**
 * Discriminated union for the two profile shapes `normalizeProfileSession`
 * accepts. The `localOnly: true` literal narrows the type without an extra
 * `_kind` field, keeping the on-disk cache shape stable.
 */
export type ProfileUserInput = GoogleUser | (LocalUserProfile & { localOnly: true }) | null;

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
 * Normalize a user profile from GoogleUser or LocalUserProfile fields.
 * Reads email, name, and picture directly (flat structure, no user_metadata nesting).
 *
 * For local users, `isSignedIn` is always `true` so the home hero greets them
 * by name (same UX as a Google user). For Google users, `isSignedIn` reflects
 * whether the email claim was present.
 */
export const normalizeProfileSession = (user: ProfileUserInput): ProfileSessionViewModel => {
  // Local users: detect via the `localOnly: true` literal type guard. The
  // shape is `{ name, email | null, avatarUrl | null, localOnly: true }`.
  if (user !== null && typeof user === 'object' && 'localOnly' in user && user.localOnly === true) {
    const local = user as LocalUserProfile;
    return {
      name: toNonEmptyString(local.name) ?? DEFAULT_PROFILE_NAME,
      email: toNonEmptyString(local.email) ?? DEFAULT_PROFILE_EMAIL,
      avatarUrl: toValidHttpUrl(local.avatarUrl),
      isSignedIn: true,
    };
  }

  const google = user as GoogleUser | null | undefined;
  const email = toNonEmptyString(google?.email) ?? DEFAULT_PROFILE_EMAIL;
  const localPart = toEmailLocalPart(google?.email);
  const name = toNonEmptyString(google?.name) ?? localPart ?? DEFAULT_PROFILE_NAME;

  return {
    name,
    email,
    avatarUrl: toValidHttpUrl(google?.picture),
    isSignedIn: Boolean(google?.email),
  };
};

/**
 * Convenience function that reads profile directly from reactive authState.
 * Local users take precedence over Google fields when both are set.
 */
export function profileSessionFromAuthState(): ProfileSessionViewModel {
  if (authState.isLocalUser && authState.localUser) {
    return normalizeProfileSession(authState.localUser);
  }
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
