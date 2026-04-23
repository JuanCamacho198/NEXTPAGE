import { describe, expect, it } from "vitest";
import { getProfileInitials, normalizeProfileSession } from "$lib/domain/settings/profileSession";

describe("profileSession", () => {
  it("normalizes signed-in session values", () => {
    const viewModel = normalizeProfileSession({
      user: {
        email: "reader@example.com",
        user_metadata: {
          name: "Reader Name",
          avatar_url: "https://example.com/avatar.png",
        },
      },
    } as any);

    expect(viewModel).toEqual({
      name: "Reader Name",
      email: "reader@example.com",
      avatarUrl: "https://example.com/avatar.png",
      isSignedIn: true,
    });
  });

  it("falls back to email local-part when name is missing", () => {
    const viewModel = normalizeProfileSession({
      user: {
        email: "local-part@example.com",
        user_metadata: {},
      },
    } as any);

    expect(viewModel.name).toBe("local-part");
    expect(viewModel.email).toBe("local-part@example.com");
  });

  it("uses default placeholders when session is missing", () => {
    const viewModel = normalizeProfileSession(null);
    expect(viewModel).toEqual({
      name: "Reader",
      email: "No email available",
      avatarUrl: null,
      isSignedIn: false,
    });
  });

  it("rejects non-http avatar urls", () => {
    const viewModel = normalizeProfileSession({
      user: {
        email: "reader@example.com",
        user_metadata: {
          avatar_url: "javascript:alert('xss')",
        },
      },
    } as any);

    expect(viewModel.avatarUrl).toBeNull();
  });

  it("builds profile initials defensively", () => {
    expect(getProfileInitials("Reader Name")).toBe("RN");
    expect(getProfileInitials("Reader")).toBe("R");
    expect(getProfileInitials("   ")).toBe("R");
  });
});
