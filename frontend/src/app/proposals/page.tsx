import { QueryClient, dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { serverFetch } from "@/shared/api/server-fetch";
import { proposalKeys } from "@/entities/proposal";
import type { ProposalDto } from "@/entities/proposal";
import type { PageResponse } from "@/shared/api";
import ProposalsPageClient from "./_client";

export default async function ProposalsPage() {
  const queryClient = new QueryClient();

  await queryClient.prefetchQuery({
    queryKey: proposalKeys.list({ page: 0, size: 20 }),
    queryFn: () =>
      serverFetch<PageResponse<ProposalDto>>("/api/proposals?page=0&size=20"),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <ProposalsPageClient />
    </HydrationBoundary>
  );
}