import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  createCustomer,
  deleteCustomer,
  updateCustomer,
  type CustomerWriteRequest,
} from "./api";

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

export function useDeleteCustomer() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteCustomer(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["customers"] }),
  });
}