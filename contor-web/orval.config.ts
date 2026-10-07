import { defineConfig } from "orval";

/**
 * Generates the typed API client + TanStack Query hooks from the shared contract.
 * Output (src/lib/api/generated) is never edited by hand and never committed.
 */
export default defineConfig({
  contor: {
    input: {
      target: "../api-spec/openapi.yaml",
    },
    output: {
      mode: "tags-split",
      target: "src/lib/api/generated/endpoints",
      schemas: "src/lib/api/generated/model",
      client: "react-query",
      httpClient: "fetch",
      clean: true,
      // No barrel files: import hooks/models from their own modules (bundle-barrel-imports).
      indexFiles: false,
      override: {
        mutator: {
          path: "src/lib/api/fetcher.ts",
          name: "apiFetch",
        },
        fetch: {
          includeHttpResponseReturnType: false,
        },
      },
    },
  },
});
