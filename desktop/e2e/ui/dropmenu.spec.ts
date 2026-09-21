import { expect, test, type Page } from '@playwright/test';
import { captureViewport, gotoRoute, openApp } from '../harness/appShell';
import { DEFAULT_SEEDED_TITLE } from '../harness/tauriStub';

// Pre-swap baselines for the `DropMenu` surface, captured before slice 5
// replaces its internals with bits-ui `DropdownMenu`.
//
// The trigger is addressed by the caller's `aria-label` (`shelf.bookOptions`),
// which is part of `ShelfBookActions`' contract and therefore survives the
// swap; the open popup is asserted through one of its caller-owned items
// (`children` render verbatim in both implementations).

const TITLE = DEFAULT_SEEDED_TITLE;

function bookMenu(page: Page) {
  return page.getByRole('button', { name: `Options for ${TITLE}`, exact: true }).first();
}

async function openBookMenu(page: Page): Promise<void> {
  await bookMenu(page).click();
  await expect(page.getByRole('button', { name: 'Remove from library', exact: true })).toBeVisible();
}

test('library grid: the book action menu renders over the grid', async ({ page }) => {
  await openApp(page, { bookCount: 2 });
  await gotoRoute(page, 'library');

  await openBookMenu(page);
  await expect(page.getByRole('button', { name: 'View details', exact: true })).toBeVisible();

  await captureViewport(page, 'library-grid-book-menu.png');
});

test('library list: the in-list book action menu renders over the row', async ({ page }) => {
  await openApp(page, { bookCount: 2 });
  await gotoRoute(page, 'library');

  await page.getByRole('button', { name: 'List view', exact: true }).click();
  // `ul.grid` is `ShelfGrid`'s root: waiting for it to disappear proves the
  // list variant is mounted before the trigger is resolved, so the capture
  // cannot race the view switch.
  await expect(page.locator('#main-content ul.grid')).toHaveCount(0);

  await openBookMenu(page);
  await captureViewport(page, 'library-list-book-menu.png');
});
