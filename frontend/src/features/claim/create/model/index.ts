import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createClaim, type ClaimCreateRequest } from "../api";

export function useCreateClaim() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ClaimCreateRequest) => createClaim(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["claims"] }),
  });
}