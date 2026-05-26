import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  fetchAnalysesByCustomers,
  fetchAnalysis,
  fetchAnalysisSummary,
  fetchRecentAnalyses,
  healthAnalysisKeys,
} from "../api";

export function useAnalysesByCustomers(customerIds: (string | null | undefined)[]) {
  const ids = useMemo(
    () => [...new Set(customerIds.filter((x): x is string => !!x))].sort(),
    [customerIds],
  );
  return useQuery({
    queryKey: healthAnalysisKeys.byCustomers(ids),
    queryFn: () => fetchAnalysesByCustomers(ids),
    enabled: ids.length > 0,
    staleTime: 30_000,
  });
}

export function useAnalysis(id: string | null | undefined) {
  return useQuery({
    queryKey: healthAnalysisKeys.detail(id ?? ""),
    queryFn: () => fetchAnalysis(id as string),
    enabled: !!id,
  });
}

export function useAnalysisSummary() {
  return useQuery({
    queryKey: healthAnalysisKeys.summary(),
    queryFn: fetchAnalysisSummary,
  });
}

export function useRecentAnalyses(limit = 5) {
  return useQuery({
    queryKey: healthAnalysisKeys.recent(limit),
    queryFn: () => fetchRecentAnalyses(limit),
  });
}