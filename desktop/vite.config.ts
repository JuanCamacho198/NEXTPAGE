import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";
import tailwindcss from "@tailwindcss/vite";
import { fileURLToPath, URL } from "node:url";
import fs from "node:fs";
import path from "node:path";

function cfiLockstepPlugin() {
  return {
    name: "cfi-lockstep",
    buildStart() {
      // Gate: ensure parent CFI_RE / TERMINUS_RE stay in sync with iframe bridge string
      // We read the TS sources as text and verify inclusion without needing TS transpilation.
      const cfiBridgePath = path.resolve("src/lib/features/reader/viewer-epub/cfiBridge.ts");
      const iframePath = path.resolve("src/lib/features/reader/viewer-epub/cfiBridgeIframe.ts");
      try {
        const cfiBridge = fs.readFileSync(cfiBridgePath, "utf8");
        const iframe = fs.readFileSync(iframePath, "utf8");
        const expectedCFI = "^epubcfi\\(\\/6\\/(\\d+)!(.+)\\)$";
        const expectedTerminus = "^(\\d+):(\\d+)$";
        // Check cfiBridge exports the constants
        if (!cfiBridge.includes("export const CFI_RE") || !cfiBridge.includes(expectedCFI)) {
          throw new Error(`cfiBridge.ts missing CFI_RE ${expectedCFI}`);
        }
        if (!cfiBridge.includes("export const TERMINUS_RE") || !cfiBridge.includes(expectedTerminus)) {
          throw new Error(`cfiBridge.ts missing TERMINUS_RE ${expectedTerminus}`);
        }
        // Check iframe file contains double-escaped forms that produce single-escape runtime
        // File has \\( and \\d (2 backslashes) which after template evaluation become \( and \d in runtime.
        // The runtime string must contain the single-escape sources.
        // To simulate runtime unescaping: \\ in file -> \ in runtime
        const iframeRuntimeSim = iframe.replace(/\\\\/g, "\\");
        if (!iframeRuntimeSim.includes(expectedCFI)) {
          throw new Error(`IFRAME_CFI_BRIDGE_SCRIPT drift: missing CFI_RE ${expectedCFI} in runtime simulation`);
        }
        if (!iframeRuntimeSim.includes(expectedTerminus)) {
          throw new Error(`IFRAME_CFI_BRIDGE_SCRIPT drift: missing TERMINUS_RE ${expectedTerminus} in runtime simulation`);
        }
        // Detect double-escape bug: runtime should NOT contain \\( or \\d
        // After simulation, if we still see \\( it means file had \\\\ (4) -> runtime still has \\ (bug)
        if (iframeRuntimeSim.includes("\\\\(") || iframeRuntimeSim.includes("\\\\d")) {
          throw new Error("IFRAME_CFI_BRIDGE_SCRIPT contains double-escaped regex (\\\\( or \\\\d) — remove extra backslash");
        }
        // Also ensure assertLockstep is exported
        if (!cfiBridge.includes("export function assertLockstep")) {
          throw new Error("cfiBridge.ts missing assertLockstep export");
        }
      } catch (e) {
        // Surface as build failure
        const msg = e instanceof Error ? e.message : String(e);
        throw new Error(`[cfi-lockstep] Build blocked: ${msg}`);
      }
    },
  };
}

export default defineConfig({
  // Tauri loads files from local bundle paths in production.
  // Relative asset URLs avoid a blank window caused by absolute /assets paths.
  base: "./",
  plugins: [tailwindcss(), svelte(), cfiLockstepPlugin()],
  clearScreen: false,
  // Load .env from desktop folder (not parent)
  envDir: ".",
  define: {
    __APP_VERSION__: JSON.stringify(process.env.npm_package_version || '0.1.0')
  },
  resolve: {
    alias: {
      $lib: fileURLToPath(new URL("./src/lib", import.meta.url))
    }
  },
  server: {
    port: 1420,
    strictPort: true,
    watch: {
      ignored: ["**/src-tauri/**", "**/android/**"]
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes("pdfjs-dist")) {
            return "pdfjs";
          }
        }
      }
    }
  }
});
