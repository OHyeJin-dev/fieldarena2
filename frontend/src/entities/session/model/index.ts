import { useQuery } from "@tanstack/react-query";
import { me, sessionKeys } from "../api";

export function useMe() {
  return useQuery({
    queryKey: sessionKeys.me(),
    queryFn: me,
    retry: false,
  });
}