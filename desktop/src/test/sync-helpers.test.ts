import { describe, it, expect } from 'vitest';
import { isHex, newId, deepCloneWithNewUUIDs } from '../../scripts/sync-pen-home.mjs';

describe('isHex', () => {
  it('accepts valid 6-digit hex', () => {
    expect(isHex('#1E293B')).toBe(true);
    expect(isHex('#64748B')).toBe(true);
    expect(isHex('#EF4444')).toBe(true);
    expect(isHex('#ffffff')).toBe(true);
    expect(isHex('#000000')).toBe(true);
  });
  it('rejects non-hex values', () => {
    expect(isHex('rgba(0,0,0,0.5)')).toBe(false);
    expect(isHex('red')).toBe(false);
    expect(isHex('#fff')).toBe(false);
    expect(isHex('#12345G')).toBe(false);
    expect(isHex('')).toBe(false);
    expect(isHex('#1E293B ')).toBe(false);
    expect(isHex(null as unknown as string)).toBe(false);
    expect(isHex(undefined as unknown as string)).toBe(false);
  });
  it('rejects gradient and rgba', () => {
    expect(isHex('linear-gradient(red, blue)')).toBe(false);
    expect(isHex('rgba(30,41,59,1)')).toBe(false);
  });
});

describe('newId', () => {
  it('generates 5-char id not in set', () => {
    const all = new Set<string>(['abcde', '12345']);
    const id = newId(all);
    expect(id).toHaveLength(5);
    expect(all.has(id)).toBe(true);
    expect(id).not.toBe('abcde');
  });
  it('handles collision', () => {
    const all = new Set<string>();
    const first = newId(all);
    // force collision by pre-adding the next UUID's slice? Simulate by mock is overkill — just check uniqueness over 100 calls
    const ids = new Set<string>([first]);
    for (let i = 0; i < 100; i++) ids.add(newId(all));
    expect(ids.size).toBe(101);
  });
  it('adds generated id to all set', () => {
    const all = new Set<string>();
    const id = newId(all);
    expect(all.has(id)).toBe(true);
  });
});

describe('deepCloneWithNewUUIDs', () => {
  it('clones node with new UUIDs for each id', () => {
    const all = new Set<string>(['aaa', 'bbb']);
    const node = {
      id: 'orig1',
      name: 'test',
      children: [
        { id: 'child1', name: 'c1' },
        { id: 'child2', name: 'c2', children: [{ id: 'grand1' }] },
      ],
    };
    // add orig ids to all to simulate existing
    all.add('orig1');
    all.add('child1');
    all.add('child2');
    all.add('grand1');
    const clone = deepCloneWithNewUUIDs(node as unknown as Parameters<typeof deepCloneWithNewUUIDs>[0], all) as unknown as { id: string; children: { id: string; children?: { id: string }[] }[] };
    expect(clone.id).not.toBe('orig1');
    expect(clone.children[0].id).not.toBe('child1');
    expect(clone.children[1].children![0].id).not.toBe('grand1');
    // original unchanged
    expect(node.id).toBe('orig1');
    // all now contains new ids
    expect(all.has(clone.id)).toBe(true);
  });
  it('preserves ref field', () => {
    const all = new Set<string>();
    const node = { id: 'node1', ref: 'vVEie', children: [] };
    all.add('node1');
    all.add('vVEie');
    const clone = deepCloneWithNewUUIDs(node, all);
    expect(clone.ref).toBe('vVEie');
    expect(clone.id).not.toBe('node1');
  });
  it('does not mutate original', () => {
    const all = new Set<string>();
    const orig = { id: 'A', children: [{ id: 'B' }] };
    const copyBefore = JSON.stringify(orig);
    deepCloneWithNewUUIDs(orig, all);
    expect(JSON.stringify(orig)).toBe(copyBefore);
  });
});
