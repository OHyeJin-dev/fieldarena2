import { useQuery } from "@tanstack/react-query";
import { proposalKeys, fetchProposals, type ProposalQuery } from "../api";

export function useProposals(query: ProposalQuery = {}) {
  return useQuery({
    queryKey: proposalKeys.list(query),
    queryFn: () => fetchProposals(query),
  });
}