# Test doubles boundary

MSW (`supabaseHandlers.ts`) covers **HTTP only**: Supabase PostgREST
(`/rest/v1/*`) and Auth (`/auth/v1/*`) seams, intercepted at the network
layer in vitest. No production code paths are altered; handlers are
imported by tests alone.

Tauri IPC (`invoke`) is **never** routed via MSW — the webview bridge is
not HTTP and MSW cannot observe it. Per-test invoke stubs live in
`invokeStub.ts` (`stubInvoke` / `resetInvokeStubs` / `useInvokeStubs`)
on top of the existing `@tauri-apps/api/core` mock in `src/test/setup.ts`.
Always reset between tests so stubs never leak across cases.
