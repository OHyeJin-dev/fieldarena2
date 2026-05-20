import { apiFetch } from "@/shared/api";

export interface RecentProposalDto {
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

export interface DashboardSummaryDto {
  activeProposals: number;
  underwritingPending: number;
  claimsInProgress: number;
  monthlyProposals: number;
  myCustomers: number;
  monthlyClaims: number;
  recentProposals: RecentProposalDto[];
}

export function fetchDashboardSummary(): Promise<DashboardSummaryDto> {
  return apiFetch<DashboardSummaryDto>("/api/dashboard/summary");
}