import type { NextConfig } from "next";

// The browser only ever talks to this origin. /api/* is proxied server-side to Spring,
// so no CORS is needed and the session/CSRF cookies stay first-party.
// Note: rewrites are resolved at BUILD time, so CONTOR_API_URL must be set for `next build` (and for `next start`,
// where server-side code such as the setup-status check reads it at runtime).
const apiUrl = process.env.CONTOR_API_URL ?? "http://127.0.0.1:8080";
const https = process.env.CONTOR_HTTPS === "true";

const securityHeaders = [
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "no-referrer" },
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=()" },
  ...(https ? [{ key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains" }] : []),
];

const nextConfig: NextConfig = {
  // E2E production runs build into their own folder (NEXT_DIST_DIR=.next-e2e) so they never replace the app's build.
  distDir: process.env.NEXT_DIST_DIR ?? ".next",
  // No cacheComponents/partial prerendering: every page is rendered per request so the CSP nonce (src/proxy.ts)
  // can be applied, and all content is per-user behind a session anyway.
  poweredByHeader: false,
  experimental: {
    optimizePackageImports: ["lucide-react"],
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }];
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiUrl}/api/:path*` }];
  },
};

export default nextConfig;
