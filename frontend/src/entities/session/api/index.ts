import { apiFetch } from "@/shared/api";

export interface MeResponse {
  id: string;
  role: string;
}

export function me(): Promise<MeResponse> {
  return apiFetch<MeResponse>("/api/auth/me");
}