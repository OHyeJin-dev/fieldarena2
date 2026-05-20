import { useQuery } from "@tanstack/react-query";
import { me } from "../api";

export function useMe() {
  return useQuery({
    queryKey: ["me"],
    queryFn: me,
    retry: false,
  });
}