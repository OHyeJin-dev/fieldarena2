import { defineConfig } from "steiger";
import fsd from "@feature-sliced/steiger-plugin";

export default defineConfig([
  ...fsd.configs.recommended,
  {
    // health-analysis slice is referenced in Phase E/F (features + pages integration)
    files: ["./src/entities/health-analysis/**"],
    rules: {
      "fsd/insignificant-slice": "off",
    },
  },
]);
