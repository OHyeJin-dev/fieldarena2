import { apiFetch } from "@/shared/api";
import type { ProposalDto } from "@/entities/proposal";
export type { ProposalDto };

export interface ProposalCreateRequest {
  customerName: string;
  phoneNumber: string;
  birthDate: string;
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