import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

export default defineConfig([
  ...fsd.configs.recommended,
  {
    // Server Components in app/ must import serverFetch directly from
    // `@/shared/api/server-fetch` (NOT via the shared/api barrel) so that
    // `next/headers` is not pulled into client bundles. This is an intentional
    // sidestep of the public API for server-only code.
    files: ["**/app/**/page.tsx"],
    rules: {
      "fsd/no-public-api-sidestep": "off",
    },
  },
]);
