import { useQuery } from "@tanstack/react-query";
import { claimKeys, fetchClaims, type ClaimQuery } from "../api";

export function useClaims(query: ClaimQuery = {}) {
  return useQuery({
    queryKey: claimKeys.list(query),
    queryFn: () => fetchClaims(query),
  });
}