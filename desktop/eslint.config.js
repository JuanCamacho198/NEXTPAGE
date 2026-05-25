import tsParser from '@typescript-eslint/parser';
import tsPlugin from '@typescript-eslint/eslint-plugin';
import sveltePlugin from 'eslint-plugin-svelte';
import svelteParser from 'svelte-eslint-parser';
import prettier from 'eslint-config-prettier';
import tailwindV4Canonical from './eslint-local-rules/tailwind-v4-canonical.js';

export default [
  {
    ignores: [
      'src-tauri/**',
      '**/node_modules/**',
      '**/dist/**',
      '**/build/**',
      'graphify-out/**',
    ],
  },
  {
    files: ['**/*.ts', '**/*.svelte'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        project: './tsconfig.json',
        extraFileExtensions: ['.svelte'],
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
    },
    rules: {
      ...tsPlugin.configs.recommended.rules,
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/explicit-function-return-type': 'warn',
      '@typescript-eslint/no-explicit-any': 'warn',
    },
  },
  {
    files: ['**/*.svelte'],
    languageOptions: {
      parser: svelteParser,
      parserOptions: {
        parser: tsParser,
      },
    },
    plugins: {
      svelte: sveltePlugin,
      'local-rules': {
        rules: {
          'tailwind-v4-canonical': tailwindV4Canonical,
        },
      },
    },
    rules: {
      ...sveltePlugin.configs.recommended.rules,
      'svelte/valid-each-key': 'error',
      'svelte/no-unused-svelte-ignore': 'warn',
      'local-rules/tailwind-v4-canonical': 'warn',
    },
  },
  prettier,
];
