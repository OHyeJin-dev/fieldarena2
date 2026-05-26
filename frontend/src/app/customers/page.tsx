import { QueryClient, dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { serverFetch } from "@/shared/api/server-fetch";
import { customerKeys } from "@/entities/customer";
import type { CustomerDto } from "@/entities/customer";
import type { PageResponse } from "@/shared/api";
import CustomersPageClient from "./_client";

export default async function CustomersPage() {
  const queryClient = new QueryClient();

  await queryClient.prefetchQuery({
    queryKey: customerKeys.list({ page: 0, size: 20 }),
    queryFn: () =>
      serverFetch<PageResponse<CustomerDto>>("/api/customers?page=0&size=20"),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <CustomersPageClient />
    </HydrationBoundary>
  );
}