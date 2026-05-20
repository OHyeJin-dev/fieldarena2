import { apiFetch } from "@/lib/api/csrf";
import type { ProposalDto } from "@/features/proposals/api";

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