import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { approveUser, listUsers, rejectUser } from "../api";

export function useUsers(status?: string) {
  return useQuery({
    queryKey: ["admin", "users", status],
    queryFn: () => listUsers(status),
  });
}

export function useApproveMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, role }: { id: string; role: string }) => approveUser(id, role),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
  });
}

export function useRejectMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => rejectUser(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["admin", "users"] }),
  });
}