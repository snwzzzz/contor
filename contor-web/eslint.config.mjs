import { defineConfig, globalIgnores } from "eslint/config";
import nextVitals from "eslint-config-next/core-web-vitals";
import nextTs from "eslint-config-next/typescript";

const eslintConfig = defineConfig([
  ...nextVitals,
  ...nextTs,
  // Override default ignores of eslint-config-next.
  globalIgnores([
    // Default ignores of eslint-config-next:
    ".next/**",
    ".next-e2e/**",
    "out/**",
    "build/**",
    "next-env.d.ts",
    // Generated from ../api-spec/openapi.yaml by orval. Read-only: change the spec and regenerate.
    "src/lib/api/generated/**",
    "playwright-report/**",
    "test-results/**",
  ]),
  {
    rules: {
      "@typescript-eslint/no-explicit-any": "error",
    },
  },
  {
    // Pages compose app components only; shadcn primitives (components/ui) are wrapped in components/app or
    // components/layout so look and behavior are controlled in one place (and a redesign does not touch pages).
    files: ["src/app/**/*.{ts,tsx}"],
    rules: {
      "no-restricted-imports": [
        "error",
        { patterns: [{ group: ["@/components/ui", "@/components/ui/*"], message: "Use components from @/components/app or @/components/layout in pages." }] },
      ],
    },
  },
]);

export default eslintConfig;
