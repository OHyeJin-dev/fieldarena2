import { useQuery } from "@tanstack/react-query";
import { policyKeys, fetchPolicies, type PolicyQuery } from "../api";

export function usePolicies(query: PolicyQuery = {}) {
  return useQuery({
    queryKey: policyKeys.list(query),
    queryFn: () => fetchPolicies(query),
  });
}