import { apiFetch } from "@/shared/api";

export interface UserSummary {
  id: string;
  name: string;
  phone: string;
  gaName: string;
  email: string;
  role: string | null;
  status: string;
  createdAt: string;
}

export function listUsers(status?: string): Promise<UserSummary[]> {
  const qs = status ? `?status=${encodeURIComponent(status)}` : "";
  return apiFetch<UserSummary[]>(`/api/admin/users${qs}`);
}

export function approveUser(id: string, role: string): Promise<void> {
  return apiFetch<void>(`/api/admin/users/${encodeURIComponent(id)}/approve`, {
    method: "PATCH",
    body: JSON.stringify({ role }),
  });
}

export function rejectUser(id: string): Promise<void> {
  return apiFetch<void>(`/api/admin/users/${encodeURIComponent(id)}/reject`, {
    method: "PATCH",
  });
}
