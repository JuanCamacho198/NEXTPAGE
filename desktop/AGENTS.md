# AGENTS.md

## Commands

- **Install**: `bun install`
- **Dev frontend**: `bun run dev`
- **Dev Tauri**: `bun run tauri:dev`
- **Build frontend**: `bun run build`
- **Build Tauri**: `bun run tauri:build`
- **Test all**: `bun run test`
- **Test single**: `bun vitest run --testNamePattern="test name" src/test/file.test.ts`
- **Type check**: `bun run check`

## Project Structure

- **Frontend (Svelte 5)**: `src/`
  - Components: `src/lib/components/` and `src/lib/domain/`
  - State/stores: `src/lib/stores/`
  - Types: `src/lib/types/`
  - Services: `src/lib/services/`
  - i18n: `src/lib/i18n/`
  - Tests: `src/test/` (unit/, integration/)
- **Backend (Rust/Tauri)**: `src-tauri/src/`
  - Commands: `src-tauri/src/commands.rs`
  - DB: `src-tauri/src/db.rs`
- **Database migrations**: `src-tauri/migrations/`
- **Configs**: `src-tauri/Cargo.toml`, `vite.config.ts`, `vitest.config.ts`, `tsconfig.json`

## Code Style

- **Svelte 5**: Use runes (`$state`, `$derived`, `$effect`) for reactive state
- **Imports**: Use `$lib` alias (e.g., `import { something } from '$lib/stores'`)
- **Types**: TypeScript with explicit return types on functions
- **Tests**: Vitest with `@testing-library/svelte` and jsdom

## Conventions

- State files: `*State.svelte.ts` naming convention
- Components: PascalCase `.svelte` files
- State classes: `svelte.ts` suffix
- Tests: `.test.ts` or `.test.svelte.ts` in `src/test/`
- Rust error handling: Use `thiserror` crate in `src-tauri/src/error.rs`

## Safety

- Never commit secrets; use `.env` and exclude from git
- Tauri commands run in Rust backend; validate all input in `commands.rs`

## graphify

This project has a graphify knowledge graph at graphify-out/.

Rules:
- Before answering architecture or codebase questions, read graphify-out/GRAPH_REPORT.md for god nodes and community structure
- If graphify-out/wiki/index.md exists, navigate it instead of reading raw files
- For cross-module "how does X relate to Y" questions, prefer `graphify query "<question>"`, `graphify path "<A>" "<B>"`, or `graphify explain "<concept>"` over grep — these traverse the graph's EXTRACTED + INFERRED edges instead of scanning files
- After modifying code files in this session, run `graphify update .` to keep the graph current (AST-only, no API cost)
