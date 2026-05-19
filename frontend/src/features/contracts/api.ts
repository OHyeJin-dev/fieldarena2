import { apiFetch } from "@/lib/api/csrf";

export interface PolicyDto {
  id: string;
  policyNumber: string;
  customerName: string;
  productName: string;
  insurerName: string;
  status: string;
  contractDate: string;
  monthlyPremium: number | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  pageNumber: number;
  pageSize: number;
}

export interface PolicyQuery {
  page?: number;
  size?: number;
  status?: string;
  startDate?: string;
  endDate?: string;
}

export function fetchPolicies(query: PolicyQuery = {}): Promise<PageResponse<PolicyDto>> {
  const params = new URLSearchParams();
  if (query.page !== undefined) params.set("page", String(query.page));
  if (query.size !== undefined) params.set("size", String(query.size));
  if (query.status) params.set("status", query.status);
  if (query.startDate) params.set("startDate", query.startDate);
  if (query.endDate) params.set("endDate", query.endDate);
  const qs = params.toString();
  return apiFetch<PageResponse<PolicyDto>>(`/api/policies${qs ? `?${qs}` : ""}`);
}
