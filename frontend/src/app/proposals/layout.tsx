import { AuthGuard } from "@/components/layout/auth-guard";

export default function ProposalsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AuthGuard>{children}</AuthGuard>;
}
