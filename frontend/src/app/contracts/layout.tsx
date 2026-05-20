import { AuthGuard } from "@/app/_auth-guard";

export default function ContractsLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <AuthGuard>{children}</AuthGuard>;
}
