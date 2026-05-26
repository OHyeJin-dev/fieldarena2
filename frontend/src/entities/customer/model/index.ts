import { useQuery } from "@tanstack/react-query";
import { customerKeys, fetchCustomers, type CustomerQuery } from "../api";

export function useCustomers(query: CustomerQuery = {}) {
  return useQuery({
    queryKey: customerKeys.list(query),
    queryFn: () => fetchCustomers(query),
  });
}