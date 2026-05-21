import { apiFetch } from "@/shared/api";
import type { HealthAnalysisDto, Scenario } from "@/entities/health-analysis";

export interface CreateAnalysisRequest {
  customerId: string;
  scenario: Scenario;
}

export function createAnalysis(req: CreateAnalysisRequest): Promise<HealthAnalysisDto> {
  return apiFetch<HealthAnalysisDto>("/api/health-analyses", {
    method: "POST",
    body: JSON.stringify(req),
  });
}
