#!/usr/bin/env bun
/**
 * Build-time CFI lockstep check — fails if iframe bridge regex drifts from parent.
 * Mirrors the logic in src/lib/features/reader/viewer-epub/cfiBridge.ts#assertLockstep
 * but runs as a standalone build gate (no Vite alias needed, uses relative imports).
 *
 * Usage: bun run scripts/check-cfi-lockstep.mjs
 * Exit 0 = lockstep OK, non-zero = drift detected (fails build)
 */
import { CFI_RE, TERMINUS_RE } from '../src/lib/features/reader/viewer-epub/cfiBridge.ts';
import { IFRAME_CFI_BRIDGE_SCRIPT } from '../src/lib/features/reader/viewer-epub/cfiBridgeIframe.ts';

function fail(msg) {
  console.error(`\x1b[31m[lockstep] FAIL\x1b[0m ${msg}`);
  process.exit(1);
}

function ok(msg) {
  console.log(`\x1b[32m[lockstep] OK\x1b[0m ${msg}`);
}

const cfiSource = CFI_RE.source;
const terminusSource = TERMINUS_RE.source;

if (!IFRAME_CFI_BRIDGE_SCRIPT.includes(cfiSource)) {
  fail(`IFRAME_CFI_BRIDGE_SCRIPT missing CFI_RE source ${CFI_RE}. Expected to find "${cfiSource}" in iframe script.`);
}
if (!IFRAME_CFI_BRIDGE_SCRIPT.includes(terminusSource)) {
  fail(`IFRAME_CFI_BRIDGE_SCRIPT missing TERMINUS_RE source ${TERMINUS_RE}. Expected to find "${terminusSource}" in iframe script.`);
}
if (IFRAME_CFI_BRIDGE_SCRIPT.includes('\\\\(') || IFRAME_CFI_BRIDGE_SCRIPT.includes('\\\\d')) {
  fail('IFRAME_CFI_BRIDGE_SCRIPT contains double-escaped regex (\\\\( or \\\\d) — remove extra backslash. Runtime string should have single \\ before ( and d.');
}

// Also verify via assertLockstep helper if available
try {
  const { assertLockstep } = await import('../src/lib/features/reader/viewer-epub/cfiBridge.ts');
  assertLockstep();
  ok(`assertLockstep() passed — CFI_RE ${CFI_RE} and TERMINUS_RE ${TERMINUS_RE} in sync`);
} catch (e) {
  fail(`assertLockstep() threw: ${e.message}`);
}

ok(`CFI lockstep verified: CFI_RE=${CFI_RE} TERMINUS_RE=${TERMINUS_RE}`);
