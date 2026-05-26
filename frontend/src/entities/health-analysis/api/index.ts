// frontend/src/entities/health-analysis/api/index.ts
import { apiFetch } from "@/shared/api";

export type RiskGrade = "NORMAL" | "CAUTION" | "RISK";
export type UnderwritingRecommendation = "APPROVE" | "CONDITIONAL" | "DECLINE";
export type Scenario = "RANDOM" | "NORMAL" | "HYPERTENSION" | "DIABETES" | "COMPLEX";

export interface DiseaseDto {
  code: string;
  name: string;
  diagnosedAt: string;
  frequency: string;
}

export interface HealthAnalysisDto {
  id: string;
  customerId: string;
  customerName: string;
  riskGrade: RiskGrade;
  hasDisease: boolean;
  diseases: DiseaseDto[];
  underwritingRecommendation: UnderwritingRecommendation;
  summary: string;
  analyzedAt: string;
  analyzedBy: string;
}

export interface AnalysisSummaryDto {
  total: number;
  normal: number;
  caution: number;
  risk: number;
}

export interface RecentAnalysisItemDto {
  id: string;
  customerId: string;
  customerName: string;
  riskGrade: RiskGrade;
  analyzedAt: string;
}

export function fetchAnalysesByCustomers(
  customerIds: string[],
): Promise<Record<string, HealthAnalysisDto>> {
  if (customerIds.length === 0) return Promise.resolve({});
  const params = new URLSearchParams();
  params.set("customerIds", customerIds.join(","));
  return apiFetch<Record<string, HealthAnalysisDto>>(`/api/health-analyses?${params.toString()}`);
}

export function fetchAnalysis(id: string): Promise<HealthAnalysisDto> {
  return apiFetch<HealthAnalysisDto>(`/api/health-analyses/${id}`);
}

export function fetchAnalysisSummary(): Promise<AnalysisSummaryDto> {
  return apiFetch<AnalysisSummaryDto>("/api/health-analyses/summary");
}

export function fetchRecentAnalyses(limit = 5): Promise<RecentAnalysisItemDto[]> {
  return apiFetch<RecentAnalysisItemDto[]>(`/api/health-analyses/recent?limit=${limit}`);
}

export const healthAnalysisKeys = {
  all: ["health-analyses"] as const,
  byCustomers: (ids: string[]) => ["health-analyses", "by-customers", ids] as const,
  detail: (id: string) => ["health-analyses", "by-id", id] as const,
  summary: () => ["health-analyses", "summary"] as const,
  recent: (limit: number) => ["health-analyses", "recent", limit] as const,
};
