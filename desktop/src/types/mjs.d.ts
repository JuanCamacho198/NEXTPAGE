declare module '*.mjs' {
  export function isHex(s: string): boolean;
  export function newId(all: Set<string>): string;
  export function deepCloneWithNewUUIDs<T>(node: T, all: Set<string>): T;
  export const PEN_PATH: string;
  export const BAK_PATH: string;
  export const TMP_PATH: string;
  export function buildAllIds(pen: unknown): Set<string>;
  export function findTopLevelById(pen: unknown, id: string): unknown;
  export function findByName(children: unknown[], name: string): unknown;
  export function validateRefs(refs: Set<string> | string[], all?: Set<string>): void;
  export function validateBounds(pen: unknown): void;
}
