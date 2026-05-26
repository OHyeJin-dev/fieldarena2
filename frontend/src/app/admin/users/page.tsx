import { QueryClient, dehydrate, HydrationBoundary } from "@tanstack/react-query";
import { serverFetch } from "@/shared/api/server-fetch";
import { adminUserKeys, type UserSummary } from "@/entities/user";
import AdminUsersPageClient from "./_client";

export default async function AdminUsersPage() {
  const queryClient = new QueryClient();

  await queryClient.prefetchQuery({
    queryKey: adminUserKeys.list("PENDING"),
    queryFn: () => serverFetch<UserSummary[]>("/api/admin/users?status=PENDING"),
  });

  return (
    <HydrationBoundary state={dehydrate(queryClient)}>
      <AdminUsersPageClient />
    </HydrationBoundary>
  );
}