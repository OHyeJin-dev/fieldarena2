import { useQuery } from "@tanstack/react-query";
import { fetchProposals, type ProposalQuery } from "../api";

export function useProposals(query: ProposalQuery = {}) {
  return useQuery({
    queryKey: ["proposals", query],
    queryFn: () => fetchProposals(query),
  });
}