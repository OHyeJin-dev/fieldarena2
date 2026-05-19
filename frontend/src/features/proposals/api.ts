import { apiFetch } from "@/lib/api/csrf";
import type { PageResponse } from "@/features/contracts/api";

export interface ProposalDto {
  id: string;
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

export interface ProposalCreateRequest {
  customerName: string;
  phoneNumber: string;
  birthDate: string;
  productName: string;
  insurerName: string;
  monthlyPremium: number;
}

export function fetchProposals(query: ProposalQuery = {}): Promise<PageResponse<ProposalDto>> {
  const params = new URLSearchParams();
  if (query.page !== undefined) params.set("page", String(query.page));
  if (query.size !== undefined) params.set("size", String(query.size));
  if (query.status) params.set("status", query.status);
  const qs = params.toString();
  return apiFetch<PageResponse<ProposalDto>>(`/api/proposals${qs ? `?${qs}` : ""}`);
}

export function createProposal(req: ProposalCreateRequest): Promise<ProposalDto> {
  return apiFetch<ProposalDto>("/api/proposals", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
