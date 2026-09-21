import { expect, test } from '@playwright/test';
import { captureViewport, gotoRoute, openApp, openShelfDetail } from '../harness/appShell';

// Pre-swap baselines for the `Dropdown` surface, captured before slice 6
// replaces its internals with bits-ui `Select`.
//
// Two shapes are covered: a page-level Dropdown (the library sort control) and
// the two Dropdowns that render inside an open Dialog (`ShelfDetailModal`): the
// status control in its detail view and the genre control in its edit view.
// Options are addressed by their visible label, which both implementations
// render, and the selection check asserts the value round-trips back into the
// trigger rather than asserting any internal markup.

const SORT_TRIGGER = 'Fecha agregada';
const SORT_SELECTED = 'Ultima lectura';
const STATUS_OPTION = 'To read';
const GENRE_OPTION = 'Science fiction';

test('library: the sort dropdown opens over the shelf and writes the value back', async ({
  page,
}) => {
  await openApp(page, { bookCount: 2 });
  await gotoRoute(page, 'library');

  await page.getByRole('button', { name: SORT_TRIGGER, exact: true }).click();
  await expect(page.getByText('Titulo', { exact: true })).toBeVisible();
  await captureViewport(page, 'library-sort-dropdown-open.png');

  await page.getByText(SORT_SELECTED, { exact: true }).click();

  // The popup closes and the trigger carries the new label: the value went back
  // through the bound prop exactly once, without re-rendering the shelf chrome.
  await expect(page.getByText('Titulo', { exact: true })).toBeHidden();
  await expect(page.getByRole('button', { name: SORT_SELECTED, exact: true })).toBeVisible();
  await captureViewport(page, 'library-sort-dropdown-selected.png');
});

test('shelf detail dialog: both in-dialog dropdowns open above the dialog', async ({ page }) => {
  await openApp(page, { bookCount: 2 });
  await gotoRoute(page, 'home');

  await openShelfDetail(page);

  // 1. Status dropdown, detail view.
  await page.locator('[role=dialog]').getByRole('button', { name: 'Reading', exact: true }).click();
  await expect(page.getByText(STATUS_OPTION, { exact: true })).toBeVisible();
  await captureViewport(page, 'shelf-detail-status-dropdown.png');

  // Close the popup without closing the dialog: the Dropdown closes on an
  // outside click, and the Modal only closes when the backdrop itself is the
  // event target, so a click on the dialog header closes exactly one layer.
  await page.locator('[role=dialog] h2').first().click();
  await expect(page.getByText(STATUS_OPTION, { exact: true })).toBeHidden();

  // 2. Genre dropdown, edit view of the same dialog.
  await page.locator('[role=dialog]').getByRole('button', { name: 'Edit metadata' }).click();
  await page
    .locator('[role=dialog] button')
    .filter({ hasText: 'Other' })
    .first()
    .click();
  await expect(page.getByText(GENRE_OPTION, { exact: true })).toBeVisible();
  await captureViewport(page, 'shelf-detail-genre-dropdown.png');
});
