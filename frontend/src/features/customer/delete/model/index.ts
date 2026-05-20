import { useMutation, useQueryClient } from "@tanstack/react-query";
import { deleteCustomer } from "../api";

export function useDeleteCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteCustomer(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}