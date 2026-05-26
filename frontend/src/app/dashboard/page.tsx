import { QueryClient, dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { serverFetch } from "@/shared/api/server-fetch";
import { dashboardKeys, type DashboardSummaryDto } from "@/entities/dashboard";
import { sessionKeys, type MeResponse } from "@/entities/session";
import {
  healthAnalysisKeys,
  type AnalysisSummaryDto,
  type RecentAnalysisItemDto,
} from "@/entities/health-analysis";
import DashboardPageClient from "./_client";

export default async function DashboardPage() {
  const queryClient = new QueryClient();

  await Promise.all([
    queryClient.prefetchQuery({
      queryKey: dashboardKeys.summary(),
      queryFn: () => serverFetch<DashboardSummaryDto>("/api/dashboard/summary"),
    }),
    queryClient.prefetchQuery({
      queryKey: sessionKeys.me(),
      queryFn: () => serverFetch<MeResponse>("/api/auth/me"),
    }),
    queryClient.prefetchQuery({
      queryKey: healthAnalysisKeys.summary(),
      queryFn: () => serverFetch<AnalysisSummaryDto>("/api/health-analyses/summary"),
    }),
    queryClient.prefetchQuery({
      queryKey: healthAnalysisKeys.recent(5),
      queryFn: () =>
        serverFetch<RecentAnalysisItemDto[]>("/api/health-analyses/recent?limit=5"),
    }),
  ]);

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <DashboardPageClient />
    </HydrationBoundary>
  );
}