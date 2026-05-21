import { apiFetch } from "@/shared/api";
import type { ProposalDto } from "@/entities/proposal";

export interface ProposalCreateRequest {
  customerId: string;
  productName: string;
  insurerName: string;
  monthlyPremium: number;
}

export function createProposal(req: ProposalCreateRequest): Promise<ProposalDto> {
  return apiFetch<ProposalDto>("/api/proposals", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
