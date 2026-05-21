import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

export default defineConfig([
  ...fsd.configs.recommended,
  // TODO: remove once features/contract/create is consumed by app/underwriting/page.tsx
  {
    files: ["./src/features/contract/create/**"],
    rules: {
      "fsd/insignificant-slice": "off",
    },
  },
]);
