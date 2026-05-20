import { useQuery } from "@tanstack/react-query";
import { fetchCustomers, type CustomerQuery } from "../api";

export function useCustomers(query: CustomerQuery = {}) {
  return useQuery({
    queryKey: ["customers", query],
    queryFn: () => fetchCustomers(query),
  });
}