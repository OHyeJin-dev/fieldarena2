import { apiFetch } from "@/shared/api";
import type { MeResponse } from "@/entities/session";

export interface LoginRequest {
  username: string;
  password: string;
}

export function login(body: LoginRequest): Promise<MeResponse> {
  return apiFetch<MeResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(body),
  });
}