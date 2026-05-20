import { apiFetch } from "@/shared/api";

export interface RegisterRequest {
  id: string;
  password: string;
  name: string;
  phone: string;
  gaName: string;
  email: string;
}

export function register(body: RegisterRequest): Promise<void> {
  return apiFetch<void>("/api/auth/register", {
    method: "POST",
    body: JSON.stringify(body),
  });
}