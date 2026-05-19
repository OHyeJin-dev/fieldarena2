import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createClaim, fetchClaims, type ClaimCreateRequest, type ClaimQuery } from "./api";

export function useClaims(query: ClaimQuery = {}) {
  return useQuery({
    queryKey: ["claims", query],
    queryFn: () => fetchClaims(query),
  });
}

export function useCreateClaim() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (req: ClaimCreateRequest) => createClaim(req),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["claims"] }),
  });
}
