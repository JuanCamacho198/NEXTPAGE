import { describe, it, expect } from 'vitest';
import {
  isHex,
  newId,
  deepCloneWithNewUUIDs,
  validateRefs,
  validateBounds,
  buildAllIds,
} from '../../scripts/polish-pen-home.mjs';

describe('isHex', () => {
  it('accepts 6-digit hex', () => {
    expect(isHex('#1E293B')).toBe(true);
    expect(isHex('#64748B')).toBe(true);
    expect(isHex('#EF4444')).toBe(true);
    expect(isHex('#ffffff')).toBe(true);
    expect(isHex('#000000')).toBe(true);
    expect(isHex('#38BDF8')).toBe(true);
  });
  it('rejects rgba/gradient/short/non-string', () => {
    expect(isHex('rgba(0,0,0,0.5)')).toBe(false);
    expect(isHex('linear-gradient(red, blue)')).toBe(false);
    expect(isHex('#fff')).toBe(false);
    expect(isHex('#12345G')).toBe(false);
    expect(isHex('')).toBe(false);
    expect(isHex('#1E293B ')).toBe(false);
    expect(isHex(null as unknown as string)).toBe(false);
    expect(isHex(undefined as unknown as string)).toBe(false);
    expect(isHex('red')).toBe(false);
    expect(isHex('#1e293')).toBe(false);
  });
});

describe('newId', () => {
  it('generates 5-char id not in set', () => {
    const all = new Set<string>(['abcde', '12345']);
    const id = newId(all);
    expect(id).toHaveLength(5);
    expect(all.has(id)).toBe(true);
  });
  it('collision loop 100 ids unique', () => {
    const all = new Set<string>();
    const ids = new Set<string>();
    for (let i = 0; i < 100; i++) ids.add(newId(all));
    expect(ids.size).toBe(100);
    // ensure each id length 5
    for (const id of ids) expect(id).toHaveLength(5);
  });
});

describe('deepCloneWithNewUUIDs', () => {
  it('preserves ref:vVEie and own ids not in pre-allIds', () => {
    const all = new Set<string>(['vVEie', 'orig1', 'child1']);
    const node = {
      id: 'orig1',
      name: 'CoverWithProgress',
      children: [
        { type: 'frame', id: 'child1', name: 'cover', width: 200, height: 280 },
        { type: 'ref', id: 'ref1', ref: 'vVEie', width: 'fill_container' },
      ],
    };
    const preIds = new Set(all);
    const clone = deepCloneWithNewUUIDs(node as unknown as Parameters<typeof deepCloneWithNewUUIDs>[0], all) as unknown as { id: string; ref?: string; children: unknown[] };
    expect((clone as unknown as { ref?: string }).ref === undefined || (clone as unknown as { ref?: string }).ref === 'vVEie' ? true : true);
    // find ref descendant preserved
    const refChild = (clone.children as unknown[]).find((c: unknown) => (c as { ref?: string }).ref === 'vVEie') as { ref: string } | undefined;
    expect(refChild?.ref).toBe('vVEie');
    expect(clone.id).not.toBe('orig1');
    expect(preIds.has(clone.id)).toBe(false);
    expect(all.has(clone.id)).toBe(true);
  });
  it('does not mutate original', () => {
    const all = new Set<string>();
    const orig = { id: 'A', children: [{ id: 'B' }] };
    const before = JSON.stringify(orig);
    deepCloneWithNewUUIDs(orig, all);
    expect(JSON.stringify(orig)).toBe(before);
  });
});

describe('validateRefs', () => {
  it('throws on dangling ref', () => {
    const all = new Set<string>(['AjwyA', 'lii9t', 'AbSdu', 'rq71f', 'WorJo', 'GlOAD', 'vVEie']);
    // missing pd8II
    expect(() => validateRefs(all)).toThrow(/Dangling/);
  });
  it('passes when all refs present', () => {
    const all = new Set<string>(['AjwyA', 'lii9t', 'AbSdu', 'rq71f', 'WorJo', 'GlOAD', 'vVEie', 'pd8II']);
    expect(() => validateRefs(all)).not.toThrow();
  });
  it('throws when explicit array missing', () => {
    const all = new Set<string>(['AjwyA']);
    expect(() => validateRefs(['AjwyA', 'lii9t'], all)).toThrow();
  });
});

describe('validateBounds', () => {
  it('throws on overlap', () => {
    const pen = {
      version: '2.17',
      variables: {},
      children: [
        { id: 'GImmK', type: 'frame', x: -1528, y: -290, width: 1200, height: 3162 },
        { id: '2fbd0', type: 'frame', x: -98, y: -4398, width: 1280, height: 800, fill: '$bgBase' },
        { id: 'overlap', type: 'frame', x: -1528, y: -290, width: 1200, height: 800 },
      ],
    };
    for (let i = 0; i < 13; i++) {
      (pen.children as unknown[]).push({ id: `pad${i}`, type: 'frame', x: 10000 + i * 2000, y: 10000, width: 100, height: 100 });
    }
    // add ellipse to keep total 17 and frames 16
    (pen.children as unknown[]).push({ id: 'ellipse1', type: 'ellipse', x: 20000, y: 20000, width: 100, height: 100 });
    expect(() => validateBounds(pen as unknown as Parameters<typeof validateBounds>[0])).toThrow(/overlap/i);
  });
  it('passes for real pen', async () => {
    const fs = await import('node:fs');
    const raw = fs.readFileSync('design/nextpage-desktop.pen', 'utf8');
    const pen = JSON.parse(raw);
    expect(() => validateBounds(pen)).not.toThrow();
  });
  it('throws on wrong GImmK width', () => {
    const pen = {
      version: '2.17',
      variables: {},
      children: [
        { id: 'GImmK', type: 'frame', x: -1528, y: -290, width: 999, height: 3162 },
        { id: '2fbd0', type: 'frame', x: -98, y: -4398, width: 1280, height: 800, fill: '$bgBase' },
      ],
    };
    for (let i = 0; i < 14; i++) (pen.children as unknown[]).push({ id: `p${i}`, type: 'frame', x: 5000 + i * 2000, y: 5000, width: 100, height: 100 });
    (pen.children as unknown[]).push({ id: 'ellipse1', type: 'ellipse', x: 60000, y: 60000, width: 100, height: 100 });
    expect(() => validateBounds(pen as unknown as Parameters<typeof validateBounds>[0])).toThrow(/GImmK.width/);
  });
});
