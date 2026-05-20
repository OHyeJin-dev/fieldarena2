import { AuthGuard } from "@/app/_auth-guard";

export default function ClaimsLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN", "AGENT1", "AGENT2"]}>{children}</AuthGuard>;
}
