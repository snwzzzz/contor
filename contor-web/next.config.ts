import type { NextConfig } from "next";

// The browser only ever talks to this origin. /api/* is proxied server-side to Spring,
// so no CORS is needed and the session/CSRF cookies stay first-party.
const apiUrl = process.env.CONTOR_API_URL ?? "http://127.0.0.1:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  poweredByHeader: false,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiUrl}/api/:path*` }];
  },
};

export default nextConfig;
