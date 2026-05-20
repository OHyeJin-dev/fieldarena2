import { AuthGuard } from "@/app/_auth-guard";

export default function ProposalsLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN", "AGENT1"]}>{children}</AuthGuard>;
}
