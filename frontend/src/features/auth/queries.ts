import { useMutation, useQueryClient } from "@tanstack/react-query";
import { login, logout, register, type LoginRequest, type RegisterRequest } from "./api";

export function useLoginMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: LoginRequest) => login(body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["me"] });
    },
  });
}

export function useLogoutMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: logout,
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ["me"] });
    },
  });
}

export function useRegisterMutation() {
  return useMutation({
    mutationFn: (body: RegisterRequest) => register(body),
  });
}
