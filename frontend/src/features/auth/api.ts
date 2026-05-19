import { apiFetch } from "@/lib/api/csrf";

export interface LoginRequest {
  username: string;
  password: string;
}

export interface MeResponse {
  username: string;
}

export function login(body: LoginRequest): Promise<MeResponse> {
  return apiFetch<MeResponse>("/api/auth/login", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function logout(): Promise<void> {
  return apiFetch<void>("/api/auth/logout", { method: "POST" });
}

export function me(): Promise<MeResponse> {
  return apiFetch<MeResponse>("/api/auth/me");
}
