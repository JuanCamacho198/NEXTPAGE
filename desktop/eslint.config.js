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
    files: ['**/*.ts'],
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
      '@typescript-eslint/explicit-function-return-type': ['warn', {
        allowExpressions: true,
        allowTypedFunctionExpressions: true,
        allowHigherOrderFunctions: true,
        allowDirectConstAssertionInArrowFunctions: true,
      }],
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
      '@typescript-eslint': tsPlugin,
      svelte: sveltePlugin,
      'local-rules': {
        rules: {
          'tailwind-v4-canonical': tailwindV4Canonical,
        },
      },
    },
    rules: {
      ...sveltePlugin.configs.recommended.rules,
      ...tsPlugin.configs.recommended.rules,
      'svelte/valid-each-key': 'error',
      'svelte/no-unused-svelte-ignore': 'warn',
      '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
      '@typescript-eslint/explicit-function-return-type': ['warn', {
        allowExpressions: true,
        allowTypedFunctionExpressions: true,
        allowHigherOrderFunctions: true,
        allowDirectConstAssertionInArrowFunctions: true,
      }],
      '@typescript-eslint/no-explicit-any': 'warn',
      'local-rules/tailwind-v4-canonical': 'warn',
    },
  },
  {
    files: ['src/test/**/*.ts', 'src/test/**/*.svelte'],
    rules: {
      '@typescript-eslint/explicit-function-return-type': 'off',
      '@typescript-eslint/no-explicit-any': 'off',
    },
  },
  {
    // P3-2 + P5: ban legacy auth stores and direct tauriClient outside adapters (P5 debt ~15 sites accepted, P6 will enforce full ban)
    files: ['src/**/*.{ts,svelte}'],
    rules: {
      'no-restricted-imports': ['error', {
        paths: [
          { name: '$lib/stores/authState.svelte', message: "Use '$lib/shared/stores/AuthState.svelte' instead (P3-2)." },
          { name: '$lib/stores/authState.svelte.ts', message: "Use '$lib/shared/stores/AuthState.svelte' instead (P3-2)." },
          { name: '$lib/stores/authState', message: "Use '$lib/shared/stores/AuthState.svelte' instead (P3-2)." },
          { name: '$lib/stores/authPersistence', message: "Use '$lib/shared/stores/authPersistence' instead (P3-2)." },
          { name: '$lib/stores/devicesState.svelte', message: "Use '$lib/shared/stores/DevicesState.svelte' instead (P3-2)." },
          { name: '$lib/stores/devicesState.svelte.ts', message: "Use '$lib/shared/stores/DevicesState.svelte' instead (P3-2)." },
          { name: '$lib/stores/devicesState', message: "Use '$lib/shared/stores/DevicesState.svelte' instead (P3-2)." },
          { name: '$lib/stores/toastQueue.svelte', message: "Use '$lib/shared/stores/ToastQueue.svelte' instead (P3-2)." },
          { name: '$lib/stores/toastQueue.svelte.ts', message: "Use '$lib/shared/stores/ToastQueue.svelte' instead (P3-2)." },
          { name: '$lib/stores/toastQueue', message: "Use '$lib/shared/stores/ToastQueue.svelte' instead (P3-2)." },
          { name: '$lib/shared/api/tauriClient', message: "Direct tauriClient import is forbidden outside shared/ports/adapters/tauri/** — use LibraryPort/SettingsPort/ViewerPort via adapter (P5 seam). Allowed only in shared/ports/adapters/tauri/** and shared/api." },
        ],
        patterns: [{
          group: ['$lib/stores/authState*', '$lib/stores/authPersistence*', '$lib/stores/devicesState*', '$lib/stores/toastQueue*'],
          message: "Legacy '$lib/stores/*' is banned — use '$lib/shared/stores/*' (P3-2). Shims at src/lib/stores/* are the only allowed legacy re-exports.",
        }],
      }],
    },
  },
  {
    // Shim re-exports are the only allowed legacy files — suppress the ban inside them
    files: ['src/lib/stores/authState.svelte.ts', 'src/lib/stores/devicesState.svelte.ts', 'src/lib/stores/toastQueue.svelte.ts', 'src/lib/stores/authPersistence.ts'],
    rules: {
      'no-restricted-imports': 'off',
    },
  },
  {
    // P5 seam: adapters and api are the only allowed direct tauriClient consumers
    files: ['src/lib/shared/ports/adapters/tauri/**', 'src/lib/shared/api/**'],
    rules: {
      'no-restricted-imports': 'off',
    },
  },
  {
    // P5 debt: tests are allowed to import tauriClient via vi.mock (P5 seam not enforced in tests)
    files: ['src/test/**'],
    rules: {
      'no-restricted-imports': 'off',
    },
  },
  prettier,
];
