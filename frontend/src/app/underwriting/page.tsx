import { QueryClient, dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { serverFetch } from "@/shared/api/server-fetch";
import { policyKeys, type PolicyDto } from "@/entities/contract";
import { healthAnalysisKeys, type HealthAnalysisDto } from "@/entities/health-analysis";
import type { PageResponse } from "@/shared/api";
import UnderwritingPageClient from "./_client";

export default async function UnderwritingPage({
  searchParams,
}: {
  searchParams: Promise<{ analysisId?: string }>;
}) {
  const { analysisId } = await searchParams;
  const queryClient = new QueryClient();

  const prefetches: Promise<unknown>[] = [
    queryClient.prefetchQuery({
      queryKey: policyKeys.list({ page: 0, size: 20 }),
      queryFn: () =>
        serverFetch<PageResponse<PolicyDto>>("/api/policies?page=0&size=20"),
    }),
  ];

  if (analysisId) {
    prefetches.push(
      queryClient.prefetchQuery({
        queryKey: healthAnalysisKeys.detail(analysisId),
        queryFn: () =>
          serverFetch<HealthAnalysisDto>(`/api/health-analyses/${analysisId}`),
      }),
    );
  }

  await Promise.all(prefetches);

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <UnderwritingPageClient />
    </HydrationBoundary>
  );
}