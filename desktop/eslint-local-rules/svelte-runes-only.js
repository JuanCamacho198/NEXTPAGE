/**
 * Custom ESLint rule: svelte-runes-only
 *
 * Rejects the legacy Svelte constructs that Svelte 5 runes syntax replaces, so
 * first-party components stay runes-only without a global `runes: true`
 * compiler force. That force cannot come back: it rejects the legacy output
 * every published `lucide-svelte` component emits (`$$props` / `<slot>`).
 *
 * ❌ `export let label`        → ✅ `let { label } = $props()`
 * ❌ `$$props`                 → ✅ `$props()`
 * ❌ `$$restProps`             → ✅ `$props()` rest/spread
 * ❌ `$: doubled = ...`        → ✅ `$derived(...)` / `$effect(...)`
 * ❌ `<slot />`                → ✅ snippets (`{@render children()}`)
 * ❌ `createEventDispatcher()` → ✅ callback props
 * ❌ `on:click={handler}`      → ✅ `onclick={handler}`
 */
const LEGACY_PROPS_IDENTIFIERS = new Set(['$$props', '$$restProps']);

/** @type {import('eslint').Rule.RuleModule} */
export default {
  meta: {
    type: 'problem',
    docs: {
      description:
        'Reject legacy Svelte constructs (export let, $$props, $$restProps, $:, <slot>, createEventDispatcher, on: directives); use runes syntax instead',
    },
    schema: [],
    messages: {
      exportLet: 'Legacy `export let` is forbidden. Declare props with `let { ... } = $props()`.',
      legacyIdentifier:
        'Legacy `{{name}}` is forbidden. Read props from `let { ... } = $props()` instead.',
      reactiveStatement:
        'Legacy `$:` reactive statement is forbidden. Use `$derived`, `$effect` or `$state` instead.',
      slotElement:
        'Legacy `<slot>` is forbidden. Use snippets (`{#snippet}` / `{@render children()}`) instead.',
      createEventDispatcher:
        'Legacy `createEventDispatcher` is forbidden. Pass callback props instead.',
      eventDirective:
        'Legacy `{{name}}` event directive is forbidden. Use the modern `on...` event attribute instead.',
    },
  },
  create(context) {
    return {
      ExportNamedDeclaration(node) {
        const declaration = node.declaration;
        if (declaration?.type === 'VariableDeclaration' && declaration.kind === 'let') {
          context.report({ node: declaration, messageId: 'exportLet' });
        }
      },
      Identifier(node) {
        if (LEGACY_PROPS_IDENTIFIERS.has(node.name)) {
          context.report({ node, messageId: 'legacyIdentifier', data: { name: node.name } });
        }
      },
      SvelteReactiveStatement(node) {
        context.report({ node, messageId: 'reactiveStatement' });
      },
      SvelteElement(node) {
        if (node.name?.type === 'SvelteName' && node.name.name === 'slot') {
          context.report({ node: node.name, messageId: 'slotElement' });
        }
      },
      CallExpression(node) {
        if (node.callee?.type === 'Identifier' && node.callee.name === 'createEventDispatcher') {
          context.report({ node, messageId: 'createEventDispatcher' });
        }
      },
      SvelteDirective(node) {
        if (node.kind !== 'EventHandler') return;
        const eventName = node.key?.name?.name ?? '';
        context.report({
          node,
          messageId: 'eventDirective',
          data: { name: `on:${eventName}` },
        });
      },
    };
  },
};
