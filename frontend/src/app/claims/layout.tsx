import { AuthGuard } from "@/components/layout/auth-guard";

export default function ClaimsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AuthGuard>{children}</AuthGuard>;
}
