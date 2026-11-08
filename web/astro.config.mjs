// driveperf.com — Astro static build (ADR-009). Output is plain files in dist/ that any static server can host
// (Firebase Hosting now, Caddy on a VPS later: ADR-010). No adapter, no SSR, no Firebase-only rendering features.
import { defineConfig } from 'astro/config';

export default defineConfig({
  site: 'https://driveperf.com',
  output: 'static',
  trailingSlash: 'never',
  build: {
    // /privacy → dist/privacy.html, served at /privacy with Hosting cleanUrls (or Caddy try_files {path}.html).
    format: 'file',
    // One external, hashed stylesheet per page: cacheable as immutable, and no inline <style> for the CSP.
    inlineStylesheets: 'never',
    assets: '_astro',
  },
  vite: {
    build: {
      // Keep every processed script external (script-src 'self' in the CSP; no hashes to maintain).
      assetsInlineLimit: 0,
    },
  },
  devToolbar: { enabled: false },
});
