import { AuthGuard } from "@/widgets/auth-guard";

export default function ProposalsLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN", "AGENT1"]}>{children}</AuthGuard>;
}
