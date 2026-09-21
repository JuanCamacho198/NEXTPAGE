/**
 * Guardrail rule test for `local-rules/svelte-runes-only`.
 *
 * Svelte 5 runes syntax replaces a fixed set of legacy constructs. Wave-1 had
 * to remove the global `runes: true` compiler force because it rejected every
 * published `lucide-svelte` component, so the guardrail lives here instead:
 * one lint rule, proven non-vacuous by this suite.
 *
 * Every rejected construct gets its own case, plus a runes-only case that must
 * produce no diagnostic. The suite fails if the rule silently stops reporting.
 */
import { RuleTester } from 'eslint';
import svelteParser from 'svelte-eslint-parser';
import tsParser from '@typescript-eslint/parser';
import svelteRunesOnly from '../../../../eslint-local-rules/svelte-runes-only.js';

const ruleTester = new RuleTester({
  languageOptions: {
    parser: svelteParser,
    parserOptions: {
      parser: tsParser,
    },
  },
});

ruleTester.run('svelte-runes-only', svelteRunesOnly, {
  valid: [
    {
      // Runes, snippets and modern event attributes: zero diagnostics expected.
      filename: 'RunesComponent.svelte',
      code: `<script lang="ts">
  let { label, count = 0 }: { label: string; count?: number } = $props();
  let doubled = $state(count * 2);
  const text = $derived(\`\${label}: \${doubled}\`);
  $effect(() => {
    console.log(text);
  });
</script>

<button onclick={() => (doubled += 1)}>{text}</button>

{#snippet footer()}<span>ok</span>{/snippet}
{@render footer()}`,
    },
    {
      // A `$:`-free file that merely mentions the legacy identifiers in
      // strings and comments must stay clean.
      filename: 'IncidentalTextComponent.svelte',
      code: `<script lang="ts">
  let { title } = $props();
  // legacy code used to write \`export let title\` and use $$props here
  const note = 'createEventDispatcher and <slot> are gone';
</script>
<span>{title}{note}</span>`,
    },
  ],
  invalid: [
    {
      filename: 'ExportLetComponent.svelte',
      code: `<script lang="ts">export let label: string = 'x';</script>`,
      errors: [{ messageId: 'exportLet' }],
    },
    {
      filename: 'PropsComponent.svelte',
      code: `<script lang="ts">const cls = $$props.class;</script>`,
      errors: [{ messageId: 'legacyIdentifier', data: { name: '$$props' } }],
    },
    {
      filename: 'RestPropsComponent.svelte',
      code: `<script lang="ts">const cls = $$restProps.class;</script>`,
      errors: [{ messageId: 'legacyIdentifier', data: { name: '$$restProps' } }],
    },
    {
      filename: 'ReactiveStatementComponent.svelte',
      code: `<script lang="ts">let a = 1; $: b = a + 1;</script>`,
      errors: [{ messageId: 'reactiveStatement' }],
    },
    {
      filename: 'SlotComponent.svelte',
      code: `<slot /><slot name="head" />`,
      errors: [{ messageId: 'slotElement' }, { messageId: 'slotElement' }],
    },
    {
      filename: 'DispatchComponent.svelte',
      code: `<script lang="ts">const dispatch = createEventDispatcher();</script>`,
      errors: [{ messageId: 'createEventDispatcher' }],
    },
    {
      filename: 'EventHandlerComponent.svelte',
      code: `<script lang="ts">const handler = (): void => {};</script><button on:click={handler}>x</button>`,
      errors: [{ messageId: 'eventDirective', data: { name: 'on:click' } }],
    },
    {
      // One legacy construct in an otherwise modern file is still caught, and
      // detection is per occurrence.
      filename: 'MixedComponent.svelte',
      code: `<script lang="ts">
  let { label } = $props();
  const dispatch = createEventDispatcher();
</script>

<button on:click={() => dispatch('go')}>{label}</button>`,
      errors: [{ messageId: 'createEventDispatcher' }, { messageId: 'eventDirective' }],
    },
  ],
});
