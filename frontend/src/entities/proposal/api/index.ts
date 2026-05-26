import { apiFetch } from "@/shared/api";
import type { PageResponse } from "@/shared/api";

export interface ProposalDto {
  id: string;
  customerId: string | null;
  customerName: string;
  phoneNumber: string;
  age: string;
  productName: string;
  insurerName: string;
  monthlyPremium: number | null;
  status: string;
  proposedDate: string;
}

export interface ProposalQuery {
  page?: number;
  size?: number;
  status?: string;
}

export function fetchProposals(query: ProposalQuery = {}): Promise<PageResponse<ProposalDto>> {
  const params = new URLSearchParams();
  if (query.page !== undefined) params.set("page", String(query.page));
  if (query.size !== undefined) params.set("size", String(query.size));
  if (query.status) params.set("status", query.status);
  const qs = params.toString();
  return apiFetch<PageResponse<ProposalDto>>(`/api/proposals${qs ? `?${qs}` : ""}`);
}

export const proposalKeys = {
  all: ["proposals"] as const,
  list: (query: ProposalQuery = {}) => ["proposals", query] as const,
};