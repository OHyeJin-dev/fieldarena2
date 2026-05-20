import { apiFetch } from "@/shared/api";
import type { CustomerDto } from "@/entities/customer";

export interface CustomerWriteRequest {
  name: string;
  phone: string;
  birthDate?: string | null;
  gender?: string | null;
  email?: string | null;
  address?: string | null;
  memo?: string | null;
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