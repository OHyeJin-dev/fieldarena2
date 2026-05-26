import { QueryClient, dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { serverFetch } from "@/shared/api/server-fetch";
import { claimKeys } from "@/entities/claim";
import type { ClaimDto } from "@/entities/claim";
import type { PageResponse } from "@/shared/api";
import ClaimsPageClient from "./_client";

export default async function ClaimsPage() {
  const queryClient = new QueryClient();

  await queryClient.prefetchQuery({
    queryKey: claimKeys.list({ page: 0, size: 20 }),
    queryFn: () =>
      serverFetch<PageResponse<ClaimDto>>("/api/claims?page=0&size=20"),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <ClaimsPageClient />
    </HydrationBoundary>
  );
}