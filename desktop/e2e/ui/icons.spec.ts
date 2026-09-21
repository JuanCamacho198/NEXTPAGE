import { expect, test, type Page } from '@playwright/test';
import { captureViewport, gotoRoute, navButton, openApp } from '../harness/appShell';

// Pre-swap baselines for the icon surfaces, captured before slices 10-14
// migrate them off the `Icon` shim.
//
// Scope: the routed surfaces that carry first-party icons (home, library,
// stats) plus the `AppSidebar` tooltip contract that D3 changes when bits-ui
// `Tooltip` replaces the CSS-only affordance. Both sidebar states are captured
// with and without the tooltip shown, because D3's contract is about the
// visible tooltip, not about the icon markup.
//
// The tooltip is a hover-only affordance: it is asserted visible/hidden around
// every capture, so a baseline can never silently record the wrong hover state.

const libraryIcon = (page: Page) => navButton(page, 'library').locator('svg').first();

const libraryTooltip = (page: Page) =>
  page.locator('aside nav [role=tooltip]').filter({ hasText: 'Library' });

// A point inside `#main-content`'s top padding: inert, so moving the mouse
// there clears the hover state without changing what is rendered underneath.
const INERT_POINT = { x: 1240, y: 40 };

test('routed icon surfaces: home, library and stats', async ({ page }) => {
  await openApp(page, { bookCount: 2 });

  await gotoRoute(page, 'home');
  await captureViewport(page, 'icons-home.png');

  await gotoRoute(page, 'library');
  await captureViewport(page, 'icons-library.png');

  await gotoRoute(page, 'stats');
  await captureViewport(page, 'icons-stats.png');
});

test('sidebar icon surface: expanded and collapsed, with and without the tooltip', async ({
  page,
}) => {
  await openApp(page, { bookCount: 2 });
  await gotoRoute(page, 'library');

  // Expanded, tooltip shown.
  await libraryIcon(page).hover();
  await expect(libraryTooltip(page)).toBeVisible();
  await captureViewport(page, 'icons-sidebar-expanded-tooltip.png');

  // Expanded, tooltip hidden (the resting state).
  await page.mouse.move(INERT_POINT.x, INERT_POINT.y);
  await expect(libraryTooltip(page)).toBeHidden();
  await captureViewport(page, 'icons-sidebar-expanded.png');

  // Collapsed, tooltip hidden. The collapse control is activated by keyboard so
  // the pointer never comes to rest on a hover-styled control.
  await page.getByRole('button', { name: 'Collapse sidebar', exact: true }).click();
  await page.mouse.move(INERT_POINT.x, INERT_POINT.y);
  await expect(page.getByRole('button', { name: 'Expand sidebar', exact: true })).toBeVisible();
  await expect(libraryTooltip(page)).toBeHidden();
  await captureViewport(page, 'icons-sidebar-collapsed.png');

  // Collapsed, tooltip shown: the collapsed sidebar has no text label, so the
  // tooltip is the whole remaining affordance for the nav item.
  await libraryIcon(page).hover();
  await expect(libraryTooltip(page)).toBeVisible();
  await captureViewport(page, 'icons-sidebar-collapsed-tooltip.png');
});
