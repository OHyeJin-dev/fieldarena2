import { useMutation, useQueryClient } from "@tanstack/react-query";
import { createProposal, type ProposalCreateRequest } from "./api";

export function useCreateProposal() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (req: ProposalCreateRequest) => createProposal(req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["proposals"] });
    },
  });
}