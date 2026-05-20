import { AuthGuard } from "@/widgets/auth-guard";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <AuthGuard allowedRoles={["ADMIN"]}>{children}</AuthGuard>;
}
