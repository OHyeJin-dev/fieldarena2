import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createPolicy, type CreatePolicyRequest } from "../api";

export function useCreatePolicy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: CreatePolicyRequest) => createPolicy(req),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["policies"] });
      qc.invalidateQueries({ queryKey: ["health-analyses"] });
    },
  });
}
