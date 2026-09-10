// emit-surfaces.mjs — prebuild emitter for crawl + machine surfaces.
//
// Reads the single source of truth for each input and writes deterministic
// artifacts into web/public/ (Astro copies public/ verbatim to dist/):
//   - sitemap.xml  (route table + ADDONS seed ids, hreflang alternates, no 404)
//   - catalog.json (ADDONS seed projection + generated-at)
//   - llms.txt     (summary + routes + catalog pointer)
//   - robots.txt   (allow all + absolute sitemap reference)
//
// Dependency-free: the ADDONS seed lives in a TypeScript module, so instead of
// a TS toolchain the script extracts the `ADDONS = [...]` array literal with a
// bounded bracket scan (string-aware) and evaluates that literal only.
// `site` is read from astro.config.mjs — never hardcoded here.

import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const publicDir = join(root, 'public');

function fail(message) {
  console.error(`emit-surfaces: ${message}`);
  process.exit(1);
}

// --- Single source: canonical origin from astro.config.mjs -------------------
const configSrc = readFileSync(join(root, 'astro.config.mjs'), 'utf8');
const siteMatch = configSrc.match(/site:\s*['"]([^'"]+)['"]/);
if (!siteMatch) fail('`site` not found in astro.config.mjs; refusing to emit with a guessed origin.');
const SITE = siteMatch[1].replace(/\/$/, '');
const abs = (p) => `${SITE}${p.startsWith('/') ? p : `/${p}`}`;

// --- Single source: ADDONS seed (bounded literal extraction, no TS toolchain) --
const addonsSrc = readFileSync(join(root, 'src/data/addons.ts'), 'utf8');
const marker = 'export const ADDONS';
const markerIdx = addonsSrc.indexOf(marker);
if (markerIdx === -1) fail('`export const ADDONS` not found in src/data/addons.ts.');
const eqIdx = addonsSrc.indexOf('=', markerIdx);
if (eqIdx === -1) fail('ADDONS assignment not found.');
const arrStart = addonsSrc.indexOf('[', eqIdx);
if (arrStart === -1) fail('ADDONS array literal not found.');

let depth = 0;
let inStr = null;
let end = -1;
for (let i = arrStart; i < addonsSrc.length; i++) {
  const c = addonsSrc[i];
  if (inStr) {
    if (c === '\\') {
      i += 1;
      continue;
    }
    if (c === inStr) inStr = null;
    continue;
  }
  if (c === "'" || c === '"' || c === '`') {
    inStr = c;
    continue;
  }
  if (c === '[') depth += 1;
  else if (c === ']') {
    depth -= 1;
    if (depth === 0) {
      end = i;
      break;
    }
  }
}
if (end === -1) fail('Unbalanced brackets while extracting the ADDONS literal.');
const literal = addonsSrc.slice(arrStart, end + 1);
let ADDONS;
try {
  ADDONS = new Function(`return (${literal});`)();
} catch (err) {
  fail(`Could not evaluate the ADDONS literal: ${err.message}`);
}
if (!Array.isArray(ADDONS) || ADDONS.length === 0) fail('ADDONS literal did not evaluate to a non-empty array.');

// --- Route table (404 excluded; param URLs never emitted) --------------------
const esIndex = ['/', '/catalogo', '/enviar', '/docs'];
const enIndex = ['/en/', '/en/catalog', '/en/submit', '/en/docs'];
const ids = ADDONS.map((a) => a.id);
const esDetail = ids.map((id) => `/catalogo/${id}`);
const enDetail = ids.map((id) => `/en/catalog/${id}`);

const pairs = [
  ...esIndex.map((es, i) => ({ es, en: enIndex[i] })),
  ...ids.map((id) => ({ es: `/catalogo/${id}`, en: `/en/catalog/${id}`, updatedAt: ADDONS.find((a) => a.id === id)?.updatedAt })),
];

function escXml(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// --- sitemap.xml --------------------------------------------------------------
const urlEntries = [];
for (const pair of pairs) {
  for (const path of [pair.es, pair.en]) {
    const lines = [
      '  <url>',
      `    <loc>${escXml(abs(path))}</loc>`,
      `    <xhtml:link rel="alternate" hreflang="es" href="${escXml(abs(pair.es))}" />`,
      `    <xhtml:link rel="alternate" hreflang="en" href="${escXml(abs(pair.en))}" />`,
      `    <xhtml:link rel="alternate" hreflang="x-default" href="${escXml(abs(pair.es))}" />`,
    ];
    if (pair.updatedAt) lines.push(`    <lastmod>${escXml(pair.updatedAt)}</lastmod>`);
    lines.push('  </url>');
    urlEntries.push(lines.join('\n'));
  }
}
const sitemap = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml">\n${urlEntries.join('\n')}\n</urlset>\n`;

// --- catalog.json ---------------------------------------------------------------
const catalog = {
  'generated-at': new Date().toISOString(),
  site: SITE,
  count: ADDONS.length,
  addons: [...ADDONS]
    .sort((a, b) => a.id.localeCompare(b.id))
    .map((a) => ({
      id: a.id,
      name: a.name,
      version: a.version,
      author: a.author,
      license: a.license,
      languages: a.languages,
      category: a.category,
      kind: a.kind,
      updatedAt: a.updatedAt,
      description: a.description,
      catalogs: a.catalogs,
      resources: a.resources,
      detailUrl: {
        es: abs(`/catalogo/${a.id}`),
        en: abs(`/en/catalog/${a.id}`),
      },
    })),
};
const catalogJson = `${JSON.stringify(catalog, null, 2)}\n`;

// --- llms.txt ---------------------------------------------------------------------
const esRoutes = [...esIndex, ...esDetail];
const enRoutes = [...enIndex, ...enDetail];
const addonBullets = [...ADDONS]
  .sort((a, b) => a.id.localeCompare(b.id))
  .map((a) => `- ${a.name} — ${a.description.en} — ${abs(`/catalogo/${a.id}`)}`);
const llms = `# NextPage Addons

> Community directory of book catalogs (addons) for NextPage. Search, filter, and open any addon for details.

## Routes

ES (default locale, no prefix):

${esIndex.map((p) => `- ${abs(p)}`).join('\n')}

${esDetail.map((p) => `- ${abs(p)}`).join('\n')}

EN (under /en/):

${enIndex.map((p) => `- ${abs(p)}`).join('\n')}

${enDetail.map((p) => `- ${abs(p)}`).join('\n')}

## Catalog

Complete machine-readable catalog: ${abs('/catalog.json')}

${addonBullets.join('\n')}

## Contribute

Propose a new addon at https://github.com/JuanCamacho198/NEXTPAGE (see the submit page).
`;

// --- robots.txt ---------------------------------------------------------------------
const robots = `User-agent: *
Allow: /

Sitemap: ${abs('/sitemap.xml')}
`;

// --- Write ----------------------------------------------------------------------------
mkdirSync(publicDir, { recursive: true });
writeFileSync(join(publicDir, 'sitemap.xml'), sitemap);
writeFileSync(join(publicDir, 'catalog.json'), catalogJson);
writeFileSync(join(publicDir, 'llms.txt'), llms);
writeFileSync(join(publicDir, 'robots.txt'), robots);

console.log(
  `emit-surfaces: site=${SITE} addons=${ADDONS.length} urls=${pairs.length * 2} ` +
    `routes=${esRoutes.length + enRoutes.length} -> public/{sitemap.xml,catalog.json,llms.txt,robots.txt}`,
);
