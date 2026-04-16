import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";
import tailwindcss from "@tailwindcss/vite";

export default defineConfig({
  // Tauri loads files from local bundle paths in production.
  // Relative asset URLs avoid a blank window caused by absolute /assets paths.
  base: "./",
  plugins: [
    tailwindcss(),
    svelte()
  ],
  clearScreen: false,
  // Load .env from desktop folder (not parent)
  envDir: ".",
  define: {
    __APP_VERSION__: JSON.stringify(process.env.npm_package_version || '0.1.0')
  },
  server: {
    port: 1420,
    strictPort: true,
    watch: {
      ignored: ["**/src-tauri/**", "**/android/**"]
    }
  }
});
