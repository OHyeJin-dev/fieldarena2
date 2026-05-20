import { apiFetch } from "@/shared/api";
import type { ClaimDto } from "@/entities/claim";

export interface ClaimCreateRequest {
  customerId: string;
  policyNumber: string;
  insurerName: string;
  claimType: string;
  claimAmount: number | null;
  claimDate: string;
}

export function createClaim(req: ClaimCreateRequest): Promise<ClaimDto> {
  return apiFetch<ClaimDto>("/api/claims", {
    method: "POST",
    body: JSON.stringify(req),
  });
}