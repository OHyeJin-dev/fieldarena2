import { apiFetch } from "@/shared/api";
import type { PolicyDto } from "@/entities/contract";

export interface CreatePolicyRequest {
  customerId: string;
  productName: string;
  insurerName: string;
  contractDate: string;       // "YYYY-MM-DD"
  monthlyPremium: number;
}

export function createPolicy(req: CreatePolicyRequest): Promise<PolicyDto> {
  return apiFetch<PolicyDto>("/api/policies", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
