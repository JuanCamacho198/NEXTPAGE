import "./styles.css";
import App from "./App.svelte";
import { mount } from "svelte";
import { onOpenUrl } from "@tauri-apps/plugin-deep-link";
import { supabase } from "./lib/api/supabase";

// Handle incoming deep links (e.g. from Google OAuth callback)
onOpenUrl((urls) => {
  console.log("Deep links received:", urls);
  for (const url of urls) {
    if (url.includes("auth-callback")) {
      // Supabase's getSession and setSession will handle auth hash in the fragment automatically if they are present in the URL.
      // However, for deep links, we might need to manually extract and set the session.
      console.log("Handling auth-callback...");
      // Supabase OAuth callback typically returns access_token in the URL hash.
      const hash = url.split("#")[1];
      if (hash) {
        const params = new URLSearchParams(hash);
        const accessToken = params.get("access_token");
        const refreshToken = params.get("refresh_token");

        if (accessToken && refreshToken) {
          supabase.auth.setSession({
            access_token: accessToken,
            refresh_token: refreshToken,
          }).then(({ data, error }) => {
            if (error) console.error("Error completing session:", error.message);
            else console.log("Session completed for user:", data.user?.email);
          });
        }
      }
    }
  }
});

const app = mount(App, {
  target: document.getElementById("app") as HTMLElement
});

export default app;
