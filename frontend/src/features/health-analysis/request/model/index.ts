import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { HealthAnalysisDto } from "@/entities/health-analysis";
import { createAnalysis, type CreateAnalysisRequest } from "../api";

export function useCreateAnalysis() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreateAnalysisRequest) => createAnalysis(req),
    onSuccess: (newAnalysis: HealthAnalysisDto) => {
      qc.invalidateQueries({ queryKey: ["health-analyses"] });
      qc.setQueryData(["health-analyses", "by-id", newAnalysis.id], newAnalysis);
    },
  });
}
