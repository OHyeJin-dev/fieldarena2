import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createProposal, fetchProposals, type ProposalCreateRequest, type ProposalQuery } from "./api";

export function useProposals(query: ProposalQuery = {}) {
  return useQuery({
    queryKey: ["proposals", query],
    queryFn: () => fetchProposals(query),
  });
}

export function useCreateProposal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: ProposalCreateRequest) => createProposal(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["proposals"] });
    },
  });
}
