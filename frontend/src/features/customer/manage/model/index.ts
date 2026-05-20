import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createCustomer, updateCustomer, type CustomerWriteRequest } from "../api";

export function useCreateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CustomerWriteRequest) => createCustomer(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}

export function useUpdateCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, req }: { id: string; req: CustomerWriteRequest }) => updateCustomer(id, req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}