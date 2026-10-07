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
]);

export default eslintConfig;
