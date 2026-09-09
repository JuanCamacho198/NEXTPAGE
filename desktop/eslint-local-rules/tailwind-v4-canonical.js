/**
 * Custom ESLint rule: tailwind-v4-canonical
 *
 * Enforces canonical Tailwind v4 syntax for CSS custom properties.
 *
 * ❌ Old (v3):  border-[var(--color-border)], text-[var(--color-primary)], hover:bg-[color:var(--color-border)]
 * ✅ Canonical: border-(--color-border), text-(--color-primary), hover:bg-(--color-border)
 *
 * Also catches:
 * ❌ break-words → ✅ wrap-break-word
 */

const OLD_VAR_PATTERN = /-\[(?:color:)?var\((--[\w-]+(?:,\s*[^)]*)?)\)\]/g;
const BREAK_WORDS_PATTERN = /\bbreak-words\b/g;

/** @type {import('eslint').Rule.RuleModule} */
export default {
  meta: {
    type: 'suggestion',
    docs: {
      description:
        'Enforce canonical Tailwind v4 syntax for CSS custom properties (use -(var) instead of -[var(...)])',
    },
    fixable: 'code',
    messages: {
      useCanonicalSyntax:
        "Use canonical Tailwind v4 syntax: '{{suggestion}}' instead of '{{actual}}'.",
      useWrapBreakWord:
        "Use 'wrap-break-word' instead of 'break-words' (renamed in Tailwind v4).",
    },
    schema: [],
  },
  create(context) {
    const sourceCode = context.sourceCode ?? context.getSourceCode();
    const text = sourceCode.getText();

    /** @type {Array<{ start: number; end: number; message: string; fix?: string }>} */
    const reports = [];

    // 1. Detect -[var(--x)] and -[color:var(--x)] patterns
    let match;
    while ((match = OLD_VAR_PATTERN.exec(text)) !== null) {
      const fullMatch = match[0];
      const varName = match[1]; // e.g., --color-border or --color-text-muted,#6b7280

      // Build canonical form: -(varName)
      const hasColorPrefix = fullMatch.includes('[color:');
      const suggestion = hasColorPrefix
        ? fullMatch.replace(/\[color:var\((--[\w-]+(?:,\s*[^)]*)?)\)\]/, `(${varName})`)
        : fullMatch.replace(/\[var\((--[\w-]+(?:,\s*[^)]*)?)\)\]/, `(${varName})`);

      reports.push({
        start: match.index,
        end: match.index + fullMatch.length,
        messageId: 'useCanonicalSyntax',
        messageArgs: { suggestion, actual: fullMatch },
        fix: suggestion,
      });
    }

    // 2. Detect break-words
    while ((match = BREAK_WORDS_PATTERN.exec(text)) !== null) {
      reports.push({
        start: match.index,
        end: match.index + match[0].length,
        messageId: 'useWrapBreakWord',
        fix: 'wrap-break-word',
      });
    }

    if (reports.length === 0) return {};

    return {
      Program(node) {
        for (const report of reports) {
          context.report({
            node,
            loc: sourceCode.getLocFromIndex(report.start),
            messageId: report.messageId,
            ...(report.messageArgs ? { data: report.messageArgs } : {}),
            fix:
              report.fix != null
                ? (fixer) => fixer.replaceTextRange([report.start, report.end], report.fix)
                : undefined,
          });
        }
      },
    };
  },
};
