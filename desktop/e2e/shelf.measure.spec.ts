import { expect, test, type Page } from '@playwright/test';
import { openApp } from './harness/appShell';

// Slice 16 — the virtualization measurement gate for `desktop-libraries-wave2`.
//
// Skipped unless `SHELF_MEASURE=1`, so the default `bun run test:e2e` run is
// untouched. Thresholds below are quoted verbatim from design section 6 and were
// frozen before this run existed; the spec never invents or loosens one.
//
// What is measured, per design section 6:
//   M1 mount time  <= 1,200 ms (1,000 tier) / <= 3,000 ms (5,000 tier)
//   M2 interaction <=   250 ms (1,000 tier) / <=   400 ms (5,000 tier)
//   M3 scroll p95  <=    25 ms (1,000 tier) / <=    40 ms (5,000 tier)
//   M4 DOM nodes   diagnostic, never a gate
//   M5 100-row control: M1 and M3 recorded for calibration only
//
// Anchors (M1) — two values are recorded for every case, never conflated:
//   * `frozen`     — `performance.now()` at the rAF after the click task, to the
//                    rAF after the Nth shelf item is attached. This is the
//                    design's anchor verbatim.
//   * `fromClick`  — the same end anchor, started inside the click task itself.
//                    Supplementary: the design's anchor cannot see work that the
//                    click task already finished, because a rAF callback runs
//                    after that work. Recorded so the main-thread mount cost is
//                    on the record either way.
//
// Deviation, recorded rather than hidden: for the **list** rendering the M1
// anchor is the view-toggle click, not the library-nav click. `ShelfGrid` vs
// `ShelfList` is component-local state (`useLibraryShelf`'s `activeView` resets
// to `grid` on every mount), so there is no way to first-mount the library route
// in list mode without a production change — and this slice changes no
// production code. The grid mount of that same run is still recorded.
//
// Deviation, recorded rather than hidden: the detail Dialog is **not mounted on
// the library route at all** (`ShelfDetailModal` has exactly one production
// mount site, `ShelfSection.svelte` on the home route). M2 is therefore
// attempted on the library rendering with an explicit bounded wait and reported
// as not satisfiable there, and measured on the surface where the Dialog does
// mount (`SHELF_MEASURE_ENV`-labelled `home` cases below) so the metric has a
// real number either way.

const MEASURE = process.env.SHELF_MEASURE === '1';

const TIERS = [100, 1_000, 5_000] as const;
type Tier = (typeof TIERS)[number];
type Rendering = 'grid' | 'list';

// Design section 6 table, verbatim. The 100 tier is the M5 calibration control
// and carries no thresholds by design.
const THRESHOLDS: Partial<Record<Tier, { m1: number; m2: number; m3: number }>> = {
  1_000: { m1: 1_200, m2: 250, m3: 25 },
  5_000: { m1: 3_000, m2: 400, m3: 40 },
};

const SEEDED_TITLE = 'Seeded Smoke Title';
const ITEM_WAIT_MS = 120_000;
const READY_WAIT_MS = 180_000;
const LIBRARY_DIALOG_WAIT_MS = 3_000;
const HOME_DIALOG_WAIT_MS = 5_000;
const SCROLL_STEPS = 120;

type FrameStats = { min: number; median: number; max: number };

type LibraryCase = {
  tier: Tier;
  rendering: Rendering;
  m1Anchor: 'library-nav' | 'view-toggle';
  m1FrozenMs: number;
  m1FromClickMs: number;
  navGridMountMs: number | null;
  itemCount: number;
  mode: string;
  m2DialogMs: number | null;
  m2WaitedMs: number;
  m2MenuOpened: boolean;
  m3P95Ms: number;
  m3Frames: FrameStats;
  m4Nodes: number;
  scrollHeight: number;
  ua: string;
};

type HomeCase = {
  tier: Tier;
  m2DialogMs: number | null;
  m2WaitedMs: number;
  cards: number;
  firstCardTitle: string;
  ua: string;
};

function p95(samples: number[]): number {
  if (samples.length === 0) return Number.NaN;
  const sorted = [...samples].sort((left, right) => left - right);
  return sorted[Math.ceil(0.95 * sorted.length) - 1] ?? Number.NaN;
}

function frameStats(deltas: number[]): FrameStats {
  const sorted = [...deltas].sort((left, right) => left - right);
  return {
    min: sorted[0] ?? Number.NaN,
    median: sorted[Math.floor(sorted.length / 2)] ?? Number.NaN,
    max: sorted[sorted.length - 1] ?? Number.NaN,
  };
}

function runMetadata(ua: string): Record<string, string> {
  return {
    dateIso: new Date().toISOString(),
    platform: `${process.platform}/${process.arch}`,
    bun: process.versions.bun ?? 'n/a',
    node: process.version,
    ua,
  };
}

function verdict(measured: number, threshold: number | undefined): string {
  if (threshold === undefined) return 'RECORDED (calibration tier, no threshold)';
  return measured <= threshold ? 'PASS' : 'FAIL';
}

function fmt(value: number): string {
  return Number.isFinite(value) ? `${value.toFixed(1)} ms` : 'n/a';
}

function reportLibrary(result: LibraryCase): void {
  const gate = THRESHOLDS[result.tier];
  const lines = [
    `SHELF_MEASURE_RESULT ${JSON.stringify({ surface: 'library', ...result, run: runMetadata(result.ua) })}`,
    `SHELF_MEASURE_TABLE tier=${result.tier} rendering=${result.rendering} anchor=${result.m1Anchor}`,
    '| metric | threshold | measured | per-row verdict |',
    '|---|---|---|---|',
    `| M1 mount (frozen anchor) | <= ${gate?.m1 ?? 'n/a'} ms | ${fmt(result.m1FrozenMs)} | ${verdict(result.m1FrozenMs, gate?.m1)} |`,
    `| M1 mount (supplementary click-task anchor) | not frozen | ${fmt(result.m1FromClickMs)} | diagnostic only |`,
    `| M1 mount of the grid nav-click in this same run | not frozen | ${result.navGridMountMs === null ? 'n/a' : fmt(result.navGridMountMs)} | diagnostic only |`,
    `| M2 interaction -> detail Dialog visible | <= ${gate?.m2 ?? 'n/a'} ms | ${result.m2DialogMs === null ? `no Dialog within ${result.m2WaitedMs} ms` : fmt(result.m2DialogMs)} | ${result.m2DialogMs === null ? 'NOT SATISFIABLE on the library route (see finding)' : verdict(result.m2DialogMs, gate?.m2)} |`,
    `| M3 scroll frame p95 (${SCROLL_STEPS} rAF-bounded steps) | <= ${gate?.m3 ?? 'n/a'} ms | ${fmt(result.m3P95Ms)} | ${verdict(result.m3P95Ms, gate?.m3)} |`,
    `| M4 DOM node count | diagnostic, not a gate | ${result.m4Nodes} nodes | recorded |`,
    `| shelf items attached (non-vacuity) | = ${result.tier} | ${result.itemCount} | ${result.itemCount === result.tier ? 'PASS' : 'FAIL'} |`,
    `| rendering actually measured (non-vacuity) | = ${result.rendering} | ${result.mode} | ${result.mode === result.rendering ? 'PASS' : 'FAIL'} |`,
    `| frame stats (M3) | - | min ${fmt(result.m3Frames.min)} / median ${fmt(result.m3Frames.median)} / max ${fmt(result.m3Frames.max)} | recorded |`,
    `| scroller scrollHeight | - | ${result.scrollHeight} px | recorded |`,
  ];
  console.log(lines.join('\n'));
}

function reportHome(result: HomeCase): void {
  const gate = THRESHOLDS[result.tier];
  console.log(
    [
      `SHELF_MEASURE_RESULT ${JSON.stringify({ surface: 'home', ...result, run: runMetadata(result.ua) })}`,
      `SHELF_MEASURE_TABLE tier=${result.tier} rendering=home-shelf-surface`,
      '| metric | threshold | measured | per-row verdict |',
      '|---|---|---|---|',
      `| M2 interaction (card click -> detail Dialog visible) | <= ${gate?.m2 ?? 'n/a'} ms | ${result.m2DialogMs === null ? `no Dialog within ${result.m2WaitedMs} ms` : fmt(result.m2DialogMs)} | ${result.m2DialogMs === null ? 'NOT SATISFIABLE' : verdict(result.m2DialogMs, gate?.m2)} |`,
      `| cards on the page (non-vacuity) | >= 1 seeded card | ${result.cards} | ${result.cards > 0 ? 'PASS' : 'FAIL'} |`,
      `| first card title | seeded title | ${result.firstCardTitle} | ${result.firstCardTitle.startsWith(SEEDED_TITLE) ? 'PASS' : 'FAIL'} |`,
    ].join('\n'),
  );
}

async function measureLibrary(page: Page, tier: Tier, rendering: Rendering): Promise<LibraryCase> {
  const raw = await page.evaluate(
    async (args) => {
      const expected = args.expected;
      const rendering = args.rendering;
      const itemWaitMs = args.itemWaitMs;
      const dialogWaitMs = args.dialogWaitMs;

      const raf = (): Promise<number> =>
        new Promise<number>((resolve) => requestAnimationFrame(() => resolve(performance.now())));
      const frame = (): Promise<void> =>
        new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

      const main = () => document.querySelector('#main-content');

      // The shelf is the widest `ul` inside the app scroller: the shelf `ul` holds
      // N items plus the add-book `li`, and every other `ul` on the route (the
      // downloads section) is empty at these seeds.
      const shelfUl = () => {
        let best = null;
        for (const candidate of Array.from(document.querySelectorAll('#main-content ul'))) {
          if (!best || candidate.children.length > best.children.length) best = candidate;
        }
        return best;
      };

      const modeOf = (element: Element): string => {
        const className = element.getAttribute('class') ?? '';
        if (/(^|\s)space-y-3(\s|$)/.test(className)) return 'list';
        if (/(^|\s)grid(\s|$)/.test(className)) return 'grid';
        return 'unknown';
      };

      const shelfState = () => {
        const list = shelfUl();
        if (!list) return { count: 0, mode: 'unknown', nthReal: false };
        const count = Math.max(0, list.children.length - 1);
        const items = list.children;
        let nthReal = false;
        if (items.length >= 2) {
          const nth = items[items.length - 2];
          const title = nth.querySelector('article h3');
          nthReal = Boolean(title && title.textContent && title.textContent.trim().length > 0);
        }
        return { count: count, mode: modeOf(list), nthReal: nthReal };
      };

      const waitForShelf = async (
        expectedCount: number,
        expectedMode: string,
        budgetMs: number,
      ): Promise<boolean> => {
        const started = performance.now();
        for (;;) {
          const state = shelfState();
          if (state.count === expectedCount && state.nthReal && state.mode === expectedMode)
            return true;
          if (performance.now() - started > budgetMs) return false;
          await frame();
        }
      };

      const waitForShelfMounted = async (budgetMs: number): Promise<boolean> => {
        const started = performance.now();
        for (;;) {
          if (shelfState().count > 0) return true;
          if (performance.now() - started > budgetMs) return false;
          await frame();
        }
      };

      // `BookCard` renders the title as a `<p>` and carries the title in the
      // article's `aria-label`, so the readiness probe matches on that instead
      // of on a heading element.
      const waitForSeededCard = async (budgetMs: number): Promise<boolean> => {
        const started = performance.now();
        for (;;) {
          const card = document.querySelector('#main-content article');
          const label = card ? String(card.getAttribute('aria-label') || '') : '';
          if (label.startsWith(args.seededTitle)) return true;
          if (performance.now() - started > budgetMs) return false;
          await frame();
        }
      };

      const click = (element: Element): void => {
        const rect = element.getBoundingClientRect();
        const options = {
          bubbles: true,
          cancelable: true,
          button: 0,
          isPrimary: true,
          pointerId: 1,
          pointerType: 'mouse',
          clientX: rect.left + rect.width / 2,
          clientY: rect.top + rect.height / 2,
        };
        element.dispatchEvent(new PointerEvent('pointerdown', options));
        element.dispatchEvent(new MouseEvent('mousedown', options));
        element.dispatchEvent(new PointerEvent('pointerup', options));
        element.dispatchEvent(new MouseEvent('mouseup', options));
        element.dispatchEvent(new MouseEvent('click', options));
      };

      const dialogVisible = () => {
        const dialog = document.querySelector('[role=dialog]');
        if (!dialog) return false;
        const rect = dialog.getBoundingClientRect();
        const text = String(dialog.textContent || '').trim();
        return rect.width > 0 && rect.height > 0 && text.length > 0;
      };

      const findButtonByText = (text: string): HTMLButtonElement | null =>
        Array.from(document.querySelectorAll('button')).find(
          (button) => String(button.textContent || '').trim() === text,
        ) || null;

      const byAriaLabel = (label: string): HTMLButtonElement | null =>
        Array.from(document.querySelectorAll('button')).find(
          (button) => button.getAttribute('aria-label') === label,
        ) || null;

      // Readiness: the library screen renders from `libraryState.books`, so wait
      // until the seeded rows are in the DOM before the measured navigation.
      // Without this the measured span would fold the boot data load into M1.
      const seeded = await waitForSeededCard(args.readyWaitMs);
      if (!seeded) throw new Error('seeded books never rendered on the home route');

      const navButtons = Array.from(document.querySelectorAll('aside nav > button'));
      const libraryNav = navButtons[1];
      if (!libraryNav) throw new Error('library nav button not found');

      const mainElement = main();
      if (!mainElement) throw new Error('main#main-content not found');

      // --- M1: library-nav click -> Nth shelf item attached (grid mount) ---
      const navClickAt = performance.now();
      click(libraryNav);
      const navFocusAt = await raf();
      const navMounted = await waitForShelfMounted(args.itemWaitMs);
      await waitForShelf(expected, 'grid', args.itemWaitMs);
      const navEndAt = await raf();
      const navGridMount = { frozen: navEndAt - navFocusAt, fromClick: navEndAt - navClickAt };
      if (!navMounted) throw new Error('shelf never mounted after the library-nav click');

      let m1Anchor = 'library-nav';
      let m1FrozenMs = navGridMount.frozen;
      let m1FromClickMs = navGridMount.fromClick;
      let navGridMountMs = navGridMount.fromClick;

      if (rendering === 'list') {
        const listToggle = byAriaLabel('List view');
        if (!listToggle) throw new Error('list view toggle button not found');
        const toggleClickAt = performance.now();
        click(listToggle);
        const toggleFocusAt = await raf();
        const listMounted = await waitForShelf(expected, 'list', args.itemWaitMs);
        const toggleEndAt = await raf();
        if (!listMounted) throw new Error('list rendering never mounted after the view toggle');
        m1Anchor = 'view-toggle';
        m1FrozenMs = toggleEndAt - toggleFocusAt;
        m1FromClickMs = toggleEndAt - toggleClickAt;
      }

      // --- M2: click a book card's "View details" -> detail Dialog visible ---
      const firstCard = document.querySelector('#main-content ul li article');
      if (!firstCard) throw new Error('no shelf card to click for M2');
      const optionsTrigger = Array.from(firstCard.querySelectorAll('button')).find((button) =>
        String(button.getAttribute('aria-label') || '').startsWith('Options for'),
      );
      if (!optionsTrigger) throw new Error('card options trigger not found for M2');

      optionsTrigger.dispatchEvent(
        new PointerEvent('pointerdown', {
          bubbles: true,
          cancelable: true,
          button: 0,
          isPrimary: true,
          pointerId: 1,
          pointerType: 'mouse',
        }),
      );
      await frame();
      let viewDetails = findButtonByText('View details');
      if (!viewDetails) {
        click(optionsTrigger);
        await frame();
        viewDetails = findButtonByText('View details');
      }
      const menuOpened = Boolean(viewDetails);
      const dialogStartedAt = performance.now();
      if (viewDetails) click(viewDetails);
      let m2DialogMs = null;
      for (;;) {
        if (dialogVisible()) {
          m2DialogMs = (await raf()) - dialogStartedAt;
          break;
        }
        if (performance.now() - dialogStartedAt > dialogWaitMs) break;
        await frame();
      }
      const m2WaitedMs = Math.round(performance.now() - dialogStartedAt);

      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await frame();

      // --- M3: 120 rAF-bounded programmatic scroll steps on the app scroller ---
      mainElement.scrollTop = 0;
      await frame();
      const maxScroll = Math.max(0, mainElement.scrollHeight - mainElement.clientHeight);
      const step = Math.max(1, Math.ceil(maxScroll / args.scrollSteps));
      const deltas: number[] = [];
      let previous = performance.now();
      for (let index = 0; index < args.scrollSteps; index += 1) {
        await new Promise((resolve) => {
          requestAnimationFrame(() => {
            mainElement.scrollTop = Math.min(maxScroll, mainElement.scrollTop + step);
            const now = performance.now();
            deltas.push(now - previous);
            previous = now;
            resolve(null);
          });
        });
      }

      // --- M4: DOM node count with the shelf mounted (diagnostic, not a gate) ---
      const m4Nodes = document.querySelectorAll('*').length;
      const finalState = shelfState();

      return {
        itemCount: finalState.count,
        mode: finalState.mode,
        m1FrozenMs,
        m1FromClickMs,
        navGridMountMs: rendering === 'grid' ? null : navGridMountMs,
        m2DialogMs,
        m2WaitedMs,
        m2MenuOpened: menuOpened,
        m3Deltas: deltas,
        m4Nodes,
        scrollHeight: mainElement.scrollHeight,
        ua: navigator.userAgent,
      };
    },
    {
      expected: tier,
      rendering,
      itemWaitMs: ITEM_WAIT_MS,
      readyWaitMs: READY_WAIT_MS,
      dialogWaitMs: LIBRARY_DIALOG_WAIT_MS,
      scrollSteps: SCROLL_STEPS,
      seededTitle: SEEDED_TITLE,
    },
  );

  const result: LibraryCase = {
    tier,
    rendering,
    m1Anchor: rendering === 'grid' ? 'library-nav' : 'view-toggle',
    m1FrozenMs: raw.m1FrozenMs,
    m1FromClickMs: raw.m1FromClickMs,
    navGridMountMs: raw.navGridMountMs,
    itemCount: raw.itemCount,
    mode: raw.mode,
    m2DialogMs: raw.m2DialogMs,
    m2WaitedMs: raw.m2WaitedMs,
    m2MenuOpened: raw.m2MenuOpened,
    m3P95Ms: p95(raw.m3Deltas),
    m3Frames: frameStats(raw.m3Deltas),
    m4Nodes: raw.m4Nodes,
    scrollHeight: raw.scrollHeight,
    ua: raw.ua,
  };

  expect(raw.m3Deltas.length, 'M3 collected exactly 120 frame deltas').toBe(SCROLL_STEPS);
  expect(result.itemCount, `shelf attached ${tier} items`).toBe(tier);
  expect(result.mode, `rendering measured is ${rendering}`).toBe(rendering);
  expect(result.m2MenuOpened, 'the card menu opened, so the M2 attempt really ran').toBe(true);

  return result;
}

async function measureHomeInteraction(page: Page, tier: Tier): Promise<HomeCase> {
  const raw = await page.evaluate(
    async (args) => {
      const raf = (): Promise<number> =>
        new Promise<number>((resolve) => requestAnimationFrame(() => resolve(performance.now())));
      const frame = (): Promise<void> =>
        new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));

      const waitForSeededCard = async (budgetMs: number): Promise<boolean> => {
        const started = performance.now();
        for (;;) {
          const card = document.querySelector('#main-content article');
          const label = card ? String(card.getAttribute('aria-label') || '') : '';
          if (label.startsWith(args.seededTitle)) return true;
          if (performance.now() - started > budgetMs) return false;
          await frame();
        }
      };

      const dialogVisible = () => {
        const dialog = document.querySelector('[role=dialog]');
        if (!dialog) return false;
        const rect = dialog.getBoundingClientRect();
        const text = String(dialog.textContent || '').trim();
        return rect.width > 0 && rect.height > 0 && text.length > 0;
      };

      const click = (element: Element): void => {
        const rect = element.getBoundingClientRect();
        const options = {
          bubbles: true,
          cancelable: true,
          button: 0,
          isPrimary: true,
          pointerId: 1,
          pointerType: 'mouse',
          clientX: rect.left + rect.width / 2,
          clientY: rect.top + rect.height / 2,
        };
        element.dispatchEvent(new PointerEvent('pointerdown', options));
        element.dispatchEvent(new MouseEvent('mousedown', options));
        element.dispatchEvent(new PointerEvent('pointerup', options));
        element.dispatchEvent(new MouseEvent('mouseup', options));
        element.dispatchEvent(new MouseEvent('click', options));
      };

      const seeded = await waitForSeededCard(args.readyWaitMs);
      if (!seeded) throw new Error('seeded books never rendered on the home route');

      const cards = Array.from(document.querySelectorAll('#main-content article'));
      const firstCard = cards[0];
      if (!firstCard) throw new Error('no book card on the home route');
      const cardLabel = String(firstCard.getAttribute('aria-label') || '');
      // `BookCard`'s first button in DOM order is its select control (`onclick`
      // is `onSelect`), the one that opens the detail Dialog.
      const selectButton = firstCard.querySelector('button');
      if (!selectButton) throw new Error('card select button not found');

      const clickAt = performance.now();
      click(selectButton);
      let m2DialogMs = null;
      for (;;) {
        if (dialogVisible()) {
          m2DialogMs = (await raf()) - clickAt;
          break;
        }
        if (performance.now() - clickAt > args.dialogWaitMs) break;
        await frame();
      }

      return {
        cards: cards.length,
        firstCardTitle: cardLabel,
        m2DialogMs,
        m2WaitedMs: Math.round(performance.now() - clickAt),
        ua: navigator.userAgent,
      };
    },
    {
      readyWaitMs: READY_WAIT_MS,
      dialogWaitMs: HOME_DIALOG_WAIT_MS,
      seededTitle: SEEDED_TITLE,
    },
  );

  const result: HomeCase = { tier, ...raw };
  expect(result.cards, 'the seeded cards rendered on the home route').toBeGreaterThan(0);
  expect(result.firstCardTitle, 'the first card is a seeded book').toContain(SEEDED_TITLE);
  return result;
}

test.describe('shelf measurement — library renderings (SHELF_MEASURE=1)', () => {
  test.skip(!MEASURE, 'measurement spec: set SHELF_MEASURE=1 to run it');
  test.describe.configure({ timeout: 900_000 });

  for (const tier of TIERS) {
    for (const rendering of ['grid', 'list'] as const) {
      test(`M1-M5 tier ${tier} rows — ${rendering} rendering`, async ({ page }) => {
        await openApp(page, { bookCount: tier });
        reportLibrary(await measureLibrary(page, tier, rendering));
      });
    }
  }
});

test.describe('shelf measurement — home surface with the detail Dialog mounted (SHELF_MEASURE=1)', () => {
  test.skip(!MEASURE, 'measurement spec: set SHELF_MEASURE=1 to run it');
  test.describe.configure({ timeout: 900_000 });

  for (const tier of [1_000, 5_000] as const) {
    test(`M2 interaction, tier ${tier} rows — home shelf surface`, async ({ page }) => {
      await openApp(page, { bookCount: tier });
      reportHome(await measureHomeInteraction(page, tier));
    });
  }
});
