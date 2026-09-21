import { render } from '@testing-library/svelte';
import { describe, it, expect } from 'vitest';
import { axe } from 'vitest-axe';
import { toHaveNoViolations } from 'vitest-axe/dist/matchers.js';
import Modal from '$lib/shared/ui/layout/Modal.svelte';

expect.extend({ toHaveNoViolations });

describe('Accessibility basics', () => {
  // The Modal case keeps its assertions unchanged; only the queried root moved.
  // bits-ui renders the dialog through `Dialog.Portal` into `document.body`, so
  // the render container is empty and asserting against it would be vacuous.
  it('modal should have no axe violations', async () => {
    render(Modal, {
      open: true,
      title: 'Test Modal',
    });
    const results = await axe(document.body);
    const assertion = toHaveNoViolations(results);
    expect(assertion.pass, assertion.message()).toBe(true);
  });

  it('closed modal should not render', () => {
    render(Modal, {
      open: false,
      title: 'Test Modal',
    });
    expect(document.body.querySelector('[role="dialog"]')).toBeNull();
  });
});
