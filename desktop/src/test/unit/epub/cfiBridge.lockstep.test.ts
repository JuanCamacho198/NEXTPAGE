import { describe, expect, it } from 'vitest';
import { CFI_RE, TERMINUS_RE, assertLockstep, setSpine, getSpineIndex } from '$lib/features/reader/viewer-epub/cfiBridge';
import { IFRAME_CFI_BRIDGE_SCRIPT } from '$lib/features/reader/viewer-epub/cfiBridgeIframe';

describe('cfiBridge lockstep', () => {
  it('exports CFI_RE with correct source', () => {
    expect(CFI_RE).toBeInstanceOf(RegExp);
    expect(CFI_RE.source).toBe('^epubcfi\\(\\/6\\/(\\d+)!(.+)\\)$');
    expect(CFI_RE.test('epubcfi(/6/4!/4/2:0)')).toBe(true); // high-level shape accepts any tail after !
    expect(CFI_RE.test('epubcfi(/6/4!/4/2,/1:0,/1:5)')).toBe(true);
    expect(CFI_RE.test('epubcfi(/6/!bad)')).toBe(false);
    expect(CFI_RE.test('not-a-cfi')).toBe(false);
  });

  it('exports TERMINUS_RE with correct source', () => {
    expect(TERMINUS_RE).toBeInstanceOf(RegExp);
    expect(TERMINUS_RE.source).toBe('^(\\d+):(\\d+)$');
    expect(TERMINUS_RE.test('1:0')).toBe(true);
    expect(TERMINUS_RE.test('12:345')).toBe(true);
    expect(TERMINUS_RE.test('a:b')).toBe(false);
  });

  it('assertLockstep passes when iframe script matches parent regexes', () => {
    expect(() => assertLockstep()).not.toThrow();
  });

  it('iframe script runtime regexes match parent sources', () => {
    // Runtime string after template evaluation contains single-escape regexes
    expect(IFRAME_CFI_BRIDGE_SCRIPT).toContain(CFI_RE.source);
    expect(IFRAME_CFI_BRIDGE_SCRIPT).toContain(TERMINUS_RE.source);
    // Ensure double-escape bug is NOT present: runtime should NOT contain double backslash before paren
    expect(IFRAME_CFI_BRIDGE_SCRIPT).not.toContain('\\\\(');
    expect(IFRAME_CFI_BRIDGE_SCRIPT).not.toContain('\\\\d');
  });

  it('setSpine normalizes backslashes and getSpineIndex finds normalized href', () => {
    setSpine(['OEBPS\\Text\\chapter1.xhtml', 'OEBPS/Text/chapter2.xhtml']);
    expect(getSpineIndex('OEBPS/Text/chapter1.xhtml')).toBe(1);
    expect(getSpineIndex('OEBPS\\Text\\chapter1.xhtml')).toBe(1);
    expect(getSpineIndex('OEBPS/Text/chapter2.xhtml')).toBe(2);
    // cleanup
    setSpine(['OEBPS/Text/chapter1.xhtml', 'OEBPS/Text/chapter2.xhtml', 'OEBPS/Text/chapter3.xhtml']);
  });
});
