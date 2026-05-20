import { AuthGuard } from "@/widgets/auth-guard";

export default function ContractsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AuthGuard>{children}</AuthGuard>;
}
