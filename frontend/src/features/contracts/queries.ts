import { useQuery } from "@tanstack/react-query";
import { fetchPolicies, type PolicyQuery } from "./api";

export function usePolicies(query: PolicyQuery = {}) {
  return useQuery({
    queryKey: ["policies", query],
    queryFn: () => fetchPolicies(query),
  });
}
