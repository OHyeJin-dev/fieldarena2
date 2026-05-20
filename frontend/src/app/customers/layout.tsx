import { AuthGuard } from "@/components/layout/auth-guard";

export default function CustomersLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN", "AGENT1", "AGENT2"]}>{children}</AuthGuard>;
}
