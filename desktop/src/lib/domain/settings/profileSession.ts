import type { Session } from "@supabase/supabase-js";

const DEFAULT_PROFILE_NAME = "Reader";
const DEFAULT_PROFILE_EMAIL = "No email available";

export type ProfileSessionViewModel = {
  name: string;
  email: string;
  avatarUrl: string | null;
  isSignedIn: boolean;
};

const toEmailLocalPart = (email: string | null | undefined) => {
  if (typeof email !== "string") {
    return null;
  }

  const normalized = email.trim();
  if (normalized.length === 0) {
    return null;
  }

  const [localPart] = normalized.split("@");
  return localPart?.trim().length ? localPart.trim() : null;
};

const toNonEmptyString = (value: unknown) => {
  if (typeof value !== "string") {
    return null;
  }

  const normalized = value.trim();
  return normalized.length > 0 ? normalized : null;
};

const toValidHttpUrl = (value: unknown) => {
  const candidate = toNonEmptyString(value);
  if (!candidate) {
    return null;
  }

  try {
    const parsed = new URL(candidate);
    if (parsed.protocol === "http:" || parsed.protocol === "https:") {
      return parsed.toString();
    }
  } catch {
    return null;
  }

  return null;
};

export const normalizeProfileSession = (
  session: Session | null | undefined,
): ProfileSessionViewModel => {
  const user = session?.user;
  const metadata = user?.user_metadata as Record<string, unknown> | undefined;

  const email = toNonEmptyString(user?.email) ?? DEFAULT_PROFILE_EMAIL;
  const localPart = toEmailLocalPart(user?.email);
  const name =
    toNonEmptyString(metadata?.name) ??
    toNonEmptyString(metadata?.full_name) ??
    localPart ??
    DEFAULT_PROFILE_NAME;

  return {
    name,
    email,
    avatarUrl: toValidHttpUrl(metadata?.avatar_url),
    isSignedIn: Boolean(user),
  };
};

export const getProfileInitials = (name: string) => {
  const words = name.trim().split(/\s+/).filter((word) => word.length > 0);
  if (words.length === 0) {
    return DEFAULT_PROFILE_NAME[0];
  }

  if (words.length === 1) {
    return words[0].slice(0, 1).toUpperCase();
  }

  return `${words[0].slice(0, 1)}${words[1].slice(0, 1)}`.toUpperCase();
};
