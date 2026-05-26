import { apiFetch } from "@/shared/api";
import type { PageResponse } from "@/shared/api";

export interface ClaimDto {
  id: string;
  policyNumber: string;
  customerName: string;
  insurerName: string;
  claimType: string;
  claimAmount: number | null;
  status: string;
  claimDate: string;
}

export interface ClaimQuery {
  page?: number;
  size?: number;
  status?: string;
}

export function fetchClaims(query: ClaimQuery = {}): Promise<PageResponse<ClaimDto>> {
  const params = new URLSearchParams();
  if (query.page !== undefined) params.set("page", String(query.page));
  if (query.size !== undefined) params.set("size", String(query.size));
  if (query.status) params.set("status", query.status);
  const qs = params.toString();
  return apiFetch<PageResponse<ClaimDto>>(`/api/claims${qs ? `?${qs}` : ""}`);
}

export const claimKeys = {
  all: ["claims"] as const,
  list: (query: ClaimQuery = {}) => ["claims", query] as const,
};