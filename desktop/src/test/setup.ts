import '@testing-library/jest-dom/vitest'
import { vi } from 'vitest';

// Global mock for Tauri IPC
vi.mock('@tauri-apps/api/core', () => ({
  invoke: vi.fn(),
}));

// Override structuredClone for jsdom/vitest (native Node.js version throws
// DataCloneError on mock objects with private slots, e.g. vitest MockProxy)
globalThis.structuredClone = (obj: unknown) => JSON.parse(JSON.stringify(obj));

// Polyfill DOMMatrix for pdfjs-dist (not available in Node.js/jsdom/happy-dom)
if (typeof globalThis.DOMMatrix === 'undefined') {
  class DOMMatrixPolyfill {
    a = 1; b = 0; c = 0; d = 1; e = 0; f = 0;
    constructor(transform?: string) {
      if (transform) {
        const m = transform.match(/matrix\(([^)]+)\)/);
        if (m) {
          const v = m[1].split(',').map(Number);
          this.a = v[0] ?? 1;
          this.b = v[1] ?? 0;
          this.c = v[2] ?? 0;
          this.d = v[3] ?? 1;
          this.e = v[4] ?? 0;
          this.f = v[5] ?? 0;
        }
      }
    }
    translate(tx = 0, ty = 0) {
      this.e += tx; this.f += ty;
      return this;
    }
    scale(sx = 1, sy = 1) {
      this.a *= sx; this.d *= sy;
      return this;
    }
    multiply(other: DOMMatrixPolyfill) {
      const { a, b, c, d, e, f } = this;
      this.a = a * other.a + c * other.b;
      this.b = b * other.a + d * other.b;
      this.c = a * other.c + c * other.d;
      this.d = b * other.c + d * other.d;
      this.e = a * other.e + c * other.f + e;
      this.f = b * other.e + d * other.f + f;
      return this;
    }
    get isIdentity() {
      return this.a === 1 && this.b === 0 && this.c === 0 && this.d === 1 && this.e === 0 && this.f === 0;
    }
    toFloat64() {
      return new Float64Array([this.a, this.b, this.c, this.d, this.e, this.f]);
    }
    toFloat32() {
      return new Float32Array([this.a, this.b, this.c, this.d, this.e, this.f]);
    }
    toJSON() {
      return { a: this.a, b: this.b, c: this.c, d: this.d, e: this.e, f: this.f };
    }
    toString() {
      return `matrix(${this.a}, ${this.b}, ${this.c}, ${this.d}, ${this.e}, ${this.f})`;
    }
  }
  globalThis.DOMMatrix = DOMMatrixPolyfill as unknown as typeof DOMMatrix;
}