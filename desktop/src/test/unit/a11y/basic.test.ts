import { render } from "@testing-library/svelte";
import { describe, it, expect } from "vitest";
import { axe } from "vitest-axe";
import { toHaveNoViolations } from "vitest-axe/dist/matchers.js";
import Modal from "$lib/shared/ui/layout/Modal.svelte";

expect.extend({ toHaveNoViolations });

describe("Accessibility basics", () => {
  it("modal should have no axe violations", async () => {
    const { container } = render(Modal, {
      open: true,
      title: "Test Modal",
    });
    const results = await axe(container);
    expect(results).toHaveNoViolations();
  });

  it("closed modal should not render", () => {
    const { container } = render(Modal, {
      open: false,
      title: "Test Modal",
    });
    expect(container.querySelector('[role="dialog"]')).toBeNull();
  });
});
