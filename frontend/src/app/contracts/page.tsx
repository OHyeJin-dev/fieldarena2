import { QueryClient, dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { serverFetch } from "@/shared/api/server-fetch";
import { policyKeys } from "@/entities/contract";
import type { PolicyDto } from "@/entities/contract";
import type { PageResponse } from "@/shared/api";
import ContractsPageClient from "./_client";

export default async function ContractsPage() {
  const queryClient = new QueryClient();

  await queryClient.prefetchQuery({
    queryKey: policyKeys.list({ page: 0, size: 20 }),
    queryFn: () =>
      serverFetch<PageResponse<PolicyDto>>("/api/policies?page=0&size=20"),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <ContractsPageClient />
    </HydrationBoundary>
  );
}