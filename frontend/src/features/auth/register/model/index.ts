import { useMutation } from "@tanstack/react-query";
import { register, type RegisterRequest } from "../api";

export function useRegisterMutation() {
  return useMutation({
    mutationFn: (body: RegisterRequest) => register(body),
  });
}