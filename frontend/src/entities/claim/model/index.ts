import { useQuery } from "@tanstack/react-query";
import { fetchClaims, type ClaimQuery } from "../api";

export function useClaims(query: ClaimQuery = {}) {
  return useQuery({
    queryKey: ["claims", query],
    queryFn: () => fetchClaims(query),
  });
}
