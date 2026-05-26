export { apiFetch, ApiError } from "./csrf";
export type { PageResponse } from "./types";
// serverFetch is intentionally NOT re-exported from this barrel.
// Importing it here would pull next/headers into client bundles via the
// entities barrels that re-use shared/api. Server Components must import
// the helper directly: `import { serverFetch } from "@/shared/api/server-fetch"`.
