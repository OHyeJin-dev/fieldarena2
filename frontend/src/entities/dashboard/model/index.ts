import { useQuery } from "@tanstack/react-query";
import { dashboardKeys, fetchDashboardSummary } from "../api";

export function useDashboardSummary() {
  return useQuery({
    queryKey: dashboardKeys.summary(),
    queryFn: fetchDashboardSummary,
  });
}