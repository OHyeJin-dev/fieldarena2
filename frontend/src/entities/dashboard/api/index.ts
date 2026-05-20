import { apiFetch } from "@/shared/api";
import type { ProposalDto } from "@/entities/proposal";

export interface DashboardSummaryDto {
  activeProposals: number;
  underwritingPending: number;
  claimsInProgress: number;
  monthlyProposals: number;
  myCustomers: number;
  monthlyClaims: number;
  recentProposals: ProposalDto[];
}

export function fetchDashboardSummary(): Promise<DashboardSummaryDto> {
  return apiFetch<DashboardSummaryDto>("/api/dashboard/summary");
}