import { apiFetch } from "@/shared/api";
import type { MeResponse } from "@/entities/session";

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  id: string;
  password: string;
  name: string;
  phone: string;
  gaName: string;
  email: string;
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

export function register(body: RegisterRequest): Promise<void> {
  return apiFetch<void>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
