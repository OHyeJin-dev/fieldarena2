import { apiFetch } from "@/lib/api/csrf";
import type { PageResponse } from "@/features/contracts/api";

export interface CustomerDto {
  id: string;
  name: string;
  phone: string;
  birthDate: string | null;
  gender: string | null;
  email: string | null;
  address: string | null;
  memo: string | null;
  createdAt: string;
}

export interface CustomerWriteRequest {
  name: string;
  phone: string;
  birthDate?: string | null;
  gender?: string | null;
  email?: string | null;
  address?: string | null;
  memo?: string | null;
}

export interface CustomerQuery {
  page?: number;
  size?: number;
}

export function fetchCustomers(query: CustomerQuery = {}): Promise<PageResponse<CustomerDto>> {
  const params = new URLSearchParams();
  if (query.page !== undefined) params.set("page", String(query.page));
  if (query.size !== undefined) params.set("size", String(query.size));
  const qs = params.toString();
  return apiFetch<PageResponse<CustomerDto>>(`/api/customers${qs ? `?${qs}` : ""}`);
}

export function createCustomer(req: CustomerWriteRequest): Promise<CustomerDto> {
  return apiFetch<CustomerDto>("/api/customers", {
    method: "POST",
    body: JSON.stringify(req),
  });
}

export function updateCustomer(id: string, req: CustomerWriteRequest): Promise<CustomerDto> {
  return apiFetch<CustomerDto>(`/api/customers/${id}`, {
    method: "PUT",
    body: JSON.stringify(req),
  });
}

export function deleteCustomer(id: string): Promise<void> {
  return apiFetch<void>(`/api/customers/${id}`, { method: "DELETE" });
}