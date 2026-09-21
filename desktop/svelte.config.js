// NOTE (desktop-libraries-wave1 PR2): the previous global `runes: true` force
// made every lucide-svelte component (legacy `$$props`/`<slot>` output in all
// published versions) a hard compile error, blocking the spec'd icon adoption.
// Svelte 5 auto-detects runes per file, so first-party runes components behave
// identically without the force; legacy output is confined to node_modules.
// If the team wants the guardrail back, add an eslint-side rule scoped to src/
// instead of a global compiler force.
export default {
  compilerOptions: {},
};
