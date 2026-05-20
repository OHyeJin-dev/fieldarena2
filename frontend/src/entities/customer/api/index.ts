import { apiFetch } from "@/shared/api";
import type { PageResponse } from "@/shared/api";

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
  createdBy: string;
  updatedAt: string;
  updatedBy: string;
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